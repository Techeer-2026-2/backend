package com.techeer.backend.domain.stats.service;

import com.techeer.backend.domain.campaign.entity.Campaign;
import com.techeer.backend.domain.campaign.repository.CampaignRepository;
import com.techeer.backend.domain.stats.dto.CampaignStatsEvent;
import com.techeer.backend.domain.stats.repository.CampaignStatsRepository;
import com.techeer.backend.global.exception.BusinessException;
import com.techeer.backend.global.exception.ErrorCode;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 캠페인 통계를 SSE 로 push 한다 (Phase 1).
 *
 * <p>구독자마다 일정 간격으로 campaign_stats 를 읽어 보낸다. 클릭이 들어올 때 바로 밀어 주는 이벤트 방식이 아니라
 * 주기 조회 방식이라 구현이 단순하고 클릭·노출 쪽 코드를 건드리지 않는다. 대신 최대 한 간격만큼 늦고, 구독자가 많아지면
 * DB 조회가 그만큼 늘어난다. Phase 3 에서 Kafka/ClickHouse 로 옮길 때 이벤트 방식으로 바꾼다.
 */
@Service
public class CampaignStatsStreamService {

    private final CampaignRepository campaignRepository;
    private final CampaignStatsRepository campaignStatsRepository;
    private final long intervalMillis;
    private final long timeoutMillis;
    private final ScheduledExecutorService scheduler;
    private final AtomicInteger activeSubscriptions = new AtomicInteger();

    public CampaignStatsStreamService(
            CampaignRepository campaignRepository,
            CampaignStatsRepository campaignStatsRepository,
            @Value("${stats.stream.interval-ms:2000}") long intervalMillis,
            @Value("${stats.stream.timeout-ms:1800000}") long timeoutMillis) {
        this.campaignRepository = campaignRepository;
        this.campaignStatsRepository = campaignStatsRepository;
        this.intervalMillis = intervalMillis;
        this.timeoutMillis = timeoutMillis;

        AtomicInteger threadNumber = new AtomicInteger(1);
        this.scheduler = Executors.newScheduledThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "stats-stream-" + threadNumber.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * 캠페인 통계 구독을 시작한다. 연결 직후 현재 값을 한 번 보내고, 이후 간격마다 다시 보낸다.
     *
     * <p>본인 캠페인만 구독할 수 있다. 소유권은 연결을 시작할 때 한 번만 확인한다. 없는 캠페인, 삭제된 캠페인,
     * 다른 광고주의 캠페인은 모두 같은 404 로 응답해서 남의 캠페인 ID 가 존재하는지 알아낼 수 없게 한다.
     *
     * @param advertiserId 로그인한 광고주 ID (access token 에서 꺼낸 값)
     * @param campaignId 구독할 캠페인 ID
     * @return 이벤트를 흘려보낼 SseEmitter
     * @throws BusinessException 캠페인이 없거나 삭제됐거나 내 캠페인이 아닌 경우
     */
    public SseEmitter subscribe(Long advertiserId, Long campaignId) {
        findOwnedCampaign(advertiserId, campaignId);

        // 한 연결의 최대 유지 시간(기본 30분). 넘으면 서버가 연결을 닫고, 클라이언트(EventSource)가 자동으로 다시 연결한다.
        SseEmitter emitter = new SseEmitter(timeoutMillis);
        activeSubscriptions.incrementAndGet();

        // 연결이 끝나면(정상 종료·타임아웃·오류) 반복 전송도 함께 멈춘다. stop 은 여러 번 불려도 한 번만 정리한다.
        AtomicReference<ScheduledFuture<?>> task = new AtomicReference<>();
        AtomicBoolean stopped = new AtomicBoolean(false);
        Runnable stop = () -> {
            if (stopped.compareAndSet(false, true)) {
                activeSubscriptions.decrementAndGet();
                ScheduledFuture<?> future = task.get();
                if (future != null) {
                    future.cancel(false);
                }
            }
        };
        emitter.onCompletion(stop);
        emitter.onTimeout(stop);
        emitter.onError(error -> stop.run());

        send(campaignId, emitter, stop);
        if (!stopped.get()) {
            task.set(scheduler.scheduleWithFixedDelay(
                    () -> send(campaignId, emitter, stop), intervalMillis, intervalMillis, TimeUnit.MILLISECONDS));
            // 예약하는 사이에 연결이 끊겨 이미 stop 이 불렸다면 방금 예약한 작업도 바로 취소한다.
            if (stopped.get()) {
                task.get().cancel(false);
            }
        }
        return emitter;
    }

    /**
     * 내 캠페인을 찾는다. 소유자가 다르거나 삭제됐으면 없는 것과 똑같이 취급한다.
     */
    private Campaign findOwnedCampaign(Long advertiserId, Long campaignId) {
        return campaignRepository.findById(campaignId)
                .filter(campaign -> campaign.getDeletedAt() == null && campaign.getUserId().equals(advertiserId))
                .orElseThrow(() -> new BusinessException(ErrorCode.CAMPAIGN_NOT_FOUND));
    }

    /**
     * 지금 열려 있는 구독 수. 연결이 끊긴 뒤에도 줄어들지 않으면 정리가 안 되고 있다는 신호이므로 테스트와 점검에 쓴다.
     */
    public int activeSubscriptionCount() {
        return activeSubscriptions.get();
    }

    /**
     * 현재 통계 한 번을 'stats' 이벤트로 보낸다. 전송에 실패하면(대개 클라이언트가 연결을 끊은 경우) 반복을 멈춘다.
     */
    private void send(Long campaignId, SseEmitter emitter, Runnable stop) {
        try {
            emitter.send(SseEmitter.event().name("stats").data(snapshot(campaignId)));
        } catch (IOException | RuntimeException e) {
            stop.run();
            emitter.completeWithError(e);
        }
    }

    /**
     * 집계 행이 아직 없는 캠페인(노출이 한 번도 없음)은 0, 0 으로 본다.
     */
    CampaignStatsEvent snapshot(Long campaignId) {
        return campaignStatsRepository.findById(campaignId)
                .map(stats -> CampaignStatsEvent.of(campaignId, stats.getSentCount(), stats.getClickCount()))
                .orElseGet(() -> CampaignStatsEvent.of(campaignId, 0, 0));
    }

    @PreDestroy
    void shutdown() {
        scheduler.shutdownNow();
    }
}
