package com.techeer.backend.domain.stats.service;

import com.techeer.backend.domain.stats.dto.CampaignTimeseriesBucket;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 캠페인 성과 리포트를 CSV 문자열로 만든다. 날짜별 추이 데이터를 그대로 쓴다.
 */
@Service
public class CampaignReportService {

    /** 엑셀이 UTF-8 을 올바르게 읽도록 붙이는 BOM */
    private static final String BOM = "﻿";
    private static final String HEADER = "날짜,노출 수,클릭 수,CTR(%),집행액(원),CPC(원),CPM(원)";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final CampaignTimeseriesService campaignTimeseriesService;

    public CampaignReportService(CampaignTimeseriesService campaignTimeseriesService) {
        this.campaignTimeseriesService = campaignTimeseriesService;
    }

    /**
     * @return UTF-8 바이트로 인코딩된 CSV (BOM 포함)
     */
    public byte[] buildDailyCsv(Long advertiserId, Long campaignId, LocalDateTime from, LocalDateTime to) {
        List<CampaignTimeseriesBucket> buckets =
                campaignTimeseriesService.getTimeseries(advertiserId, campaignId, from, to, "day");

        StringBuilder csv = new StringBuilder(BOM).append(HEADER).append("\r\n");
        for (CampaignTimeseriesBucket bucket : buckets) {
            csv.append(bucket.bucketStart().format(DATE)).append(',')
                    .append(bucket.impressionCount()).append(',')
                    .append(bucket.clickCount()).append(',')
                    .append(bucket.ctr()).append(',')
                    .append(bucket.spentAmount()).append(',')
                    .append(bucket.cpc()).append(',')
                    .append(bucket.cpm()).append("\r\n");
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }
}
