const $ = (id) => document.getElementById(id);
const colors = [
  "#7b8e60",
  "#bf9375",
  "#708b91",
  "#a9a179",
  "#979cbe",
  "#b88177",
];
let mode = "global";
let coordinates = null;
let requestNumber = 0;
let locationRequest = 0;
const time = new Intl.DateTimeFormat("ko-KR", {
  timeZone: "Asia/Seoul",
  hour: "2-digit",
  minute: "2-digit",
  hourCycle: "h23",
});

async function load() {
  const current = ++requestNumber;
  $("loading").hidden = false;
  $("error").hidden = true;
  $("empty").hidden = true;
  $("tracks").replaceChildren();
  $("refresh").disabled = true;
  $("notice").hidden = true;
  $("nearby").setAttribute("aria-pressed", String(mode === "nearby"));
  $("global").setAttribute("aria-pressed", String(mode === "global"));
  try {
    const path =
      mode === "nearby"
        ? `nearby?lat=${coordinates.lat}&lng=${coordinates.lng}&limit=20`
        : "global?limit=20";
    const result = await fetch(`/api/v1/rankings/tracks/${path}`);
    if (!result.ok)
      throw new Error(
        `차트를 불러오지 못했어요. 잠시 후 다시 시도해 주세요. (${result.status})`,
      );
    const { data } = await result.json();
    if (current !== requestNumber) return;
    $("chart-heading").textContent = data.locationLabel;
    $("period").textContent = data.rankedHour
      ? `${new Intl.DateTimeFormat("ko-KR", { timeZone: "Asia/Seoul", month: "long", day: "numeric" }).format(new Date(data.rankedHour))} · ${time.format(new Date(data.rankedHour))}–${time.format(new Date(Date.parse(data.rankedHour) + 3599000))} 집계 (한국 시간)`
      : "첫 시간별 차트를 준비하고 있어요.";
    $("updated").textContent = data.updatedAt
      ? `${time.format(new Date(data.updatedAt))} 업데이트 · 고유 완주 청취자 기준`
      : "매시 정각 업데이트";
    if (data.fallbackApplied)
      showNotice(
        `현재 주변의 청취 데이터가 적어 ${data.locationLabel}을 보여드려요.`,
      );
    if (data.status === "INSUFFICIENT_DATA")
      showNotice(
        "청취자가 충분히 모이지 않았어요. 개인의 청취 기록을 보호하기 위해 차트를 준비 중이에요.",
      );
    if (data.items.some((item) => item.track.id.startsWith("demo-")))
      showNotice(
        "지금 보이는 곡과 청취 수는 기능 확인을 위한 가상 데모 데이터입니다.",
      );
    for (const item of data.items) {
      const li = document.createElement("li");
      const button = document.createElement("button");
      button.className = "track";
      button.setAttribute(
        "aria-label",
        `${item.rank}위 ${item.track.title}, ${item.track.artistName} 상세 보기`,
      );
      // 메타데이터는 HTML 문자열로 삽입하지 않는다.
      const rank = element("span", "rank", String(item.rank).padStart(2, "0"));
      const cover = element("span", "cover", "♫");
      cover.style.setProperty(
        "--cover",
        colors[(item.rank - 1) % colors.length],
      );
      cover.setAttribute("aria-hidden", "true");
      const name = document.createElement("span");
      name.append(
        element("span", "track-name", item.track.title),
        element("span", "artist", item.track.artistName),
      );
      const listeners = element("span", "listeners", "");
      listeners.append(
        element("strong", "", `${item.uniqueListenerCount}명`),
        element("span", "", "이 들었어요"),
      );
      button.append(
        rank,
        cover,
        name,
        listeners,
        element("span", "play", "↗"),
      );
      button.addEventListener("click", () => details(item.track));
      li.append(button);
      $("tracks").append(li);
    }
    $("empty").hidden = data.items.length > 0;
  } catch (error) {
    if (current !== requestNumber) return;
    $("error").textContent = error.message;
    $("error").hidden = false;
    $("period").textContent = "새로고침으로 다시 시도할 수 있어요.";
  } finally {
    if (current === requestNumber) {
      $("loading").hidden = true;
      $("refresh").disabled = false;
    }
  }
}

function element(tag, className, text) {
  const node = document.createElement(tag);
  node.className = className;
  node.textContent = text;
  return node;
}

function showNotice(text) {
  $("notice").textContent = text;
  $("notice").hidden = false;
}

function safeUrl(value) {
  try {
    const url = new URL(value);
    return url.protocol === "https:" ? url.href : null;
  } catch {
    return null;
  }
}

function details(track) {
  $("detail-title").textContent = track.title;
  $("detail-artist").textContent = track.artistName;
  const preview = safeUrl(track.previewUrl);
  $("audio").pause();
  $("audio").removeAttribute("src");
  $("audio").hidden = !preview;
  if (preview) $("audio").src = preview;
  $("preview-note").textContent = preview
    ? "미리듣기입니다. 전체 곡 완주 랭킹에는 반영되지 않아요."
    : "이 곡은 아직 미리듣기가 등록되지 않았어요.";
  const external = safeUrl(track.externalUrl);
  $("external").hidden = !external;
  $("external").removeAttribute("href");
  if (external) $("external").href = external;
  $("detail").showModal();
}

$("nearby").addEventListener("click", () => {
  if (coordinates) {
    mode = "nearby";
    load();
    return;
  }
  if (!navigator.geolocation) {
    showNotice(
      "이 브라우저는 위치 확인을 지원하지 않아요. 전체 차트를 이용해 주세요.",
    );
    return;
  }
  $("nearby").disabled = true;
  const locating = ++locationRequest;
  showNotice("내 주변 차트를 위해 현재 위치를 확인하고 있어요.");
  navigator.geolocation.getCurrentPosition(
    (position) => {
      if (locating !== locationRequest) return;
      coordinates = {
        lat: position.coords.latitude,
        lng: position.coords.longitude,
      };
      mode = "nearby";
      $("nearby").disabled = false;
      load();
    },
    () => {
      if (locating !== locationRequest) return;
      $("nearby").disabled = false;
      showNotice(
        "위치를 확인하지 못했어요. 브라우저 위치 권한을 확인하거나 전체 차트를 이용해 주세요.",
      );
    },
    { maximumAge: 30000, timeout: 10000, enableHighAccuracy: true },
  );
});
$("global").addEventListener("click", () => {
  locationRequest++;
  $("nearby").disabled = false;
  mode = "global";
  load();
});
$("refresh").addEventListener("click", load);
$("close").addEventListener("click", () => $("detail").close());
$("detail").addEventListener("close", () => {
  $("audio").pause();
  $("audio").removeAttribute("src");
});
// 정각 직후 및 열려 있는 탭에서 1분마다 저장된 차트 갱신을 확인한다.
setInterval(() => {
  if (!document.hidden) load();
}, 60000);
document.addEventListener("visibilitychange", () => {
  if (!document.hidden) load();
});
load();
