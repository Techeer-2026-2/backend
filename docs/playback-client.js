/** 음악 앱의 실제 재생 시작/완료 콜백에 연결할 작은 클라이언트 예제. */
export function createListeningSession({ memberId, trackId, location = null }) {
  const request = {
    sessionId: crypto.randomUUID(),
    trackId,
    startedAt: new Date().toISOString(),
    location,
  };
  const sendStart = () => post("/api/v1/playback/sessions", request, memberId);
  let terminalRequest;
  return {
    sessionId: request.sessionId,
    start: sendStart,
    finish(playedRatio, completed = true) {
      terminalRequest ??= {
        eventId: crypto.randomUUID(),
        sessionId: request.sessionId,
        eventType: completed ? "TRACK_COMPLETED" : "TRACK_SKIPPED",
        playedRatio,
        occurredAt: new Date().toISOString(),
      };
      // 네트워크 실패 시 finish를 다시 호출해도 같은 ID·시각·내용으로 재시도한다.
      return post("/api/v1/playback/events", terminalRequest, memberId);
    },
  };
}

// 회원은 본문이 아니라 X-Member-Id 헤더로 보낸다(인증 도입 시 Authorization 으로 교체).
async function post(path, data, memberId) {
  const response = await fetch(path, {
    method: "POST",
    headers: { "Content-Type": "application/json", "X-Member-Id": String(memberId) },
    body: JSON.stringify(data),
  });
  if (!response.ok) throw new Error(`Playback API: ${response.status}`);
  return response.json();
}
