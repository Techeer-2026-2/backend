/** 음악 앱의 실제 재생 시작/완료 콜백에 연결할 작은 클라이언트 예제. */
export function createListeningSession({ userId, trackId, location = null }) {
  const request = {
    sessionId: crypto.randomUUID(),
    userId,
    trackId,
    startedAt: new Date().toISOString(),
    location,
  };
  const sendStart = () => post("/api/v1/playback/sessions", request);
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
      return post("/api/v1/playback/events", terminalRequest);
    },
  };
}

async function post(path, data) {
  const response = await fetch(path, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(data),
  });
  if (!response.ok) throw new Error(`Playback API: ${response.status}`);
  return response.json();
}
