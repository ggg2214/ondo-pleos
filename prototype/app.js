(function () {
  "use strict";

  const PX_PER_METER = 4.6;
  const LOOP_MIN_Y = -120;
  const LOOP_MAX_Y = 1180;
  const LOOP_SPAN_Y = LOOP_MAX_Y - LOOP_MIN_Y; // 1300px
  const DEFAULT_ACC_START_Y = 104.0;

  const state = {
    scene: "driving", // driving | stt_snapshot | cross_verified | rear_alert | arrival_summary
    speedKmh: 102,
    targetSpeedKmh: 102,
    gear: "D",
    hazardOn: false,
    decelMps2: -0.2,
    ambientTempC: 2.5,
    liveMilepostKm: 342.33,
    lockedMilepostKm: null,
    rearAlertDistKm: 2.3,
    egoLane: 3.0,
    targetEgoLane: 3.0,
    worldScrollPx: 0.0,
    roadOffset: 0.0,
    accStartScreenY: DEFAULT_ACC_START_Y,
    accSpawnScrollPx: 0.0,
    accActive: true,
    isLandscape20: false,
    isLightTheme: false,
    deterministicVideoMode: false,
    simTimeSec: 0.0,
    autoTimers: [],
    sttTyperTimer: null,
    ttsEnabled: true
  };

  // DOM References
  const el = {
    iviMain: document.getElementById("iviMain"),
    ondoWindow: document.getElementById("ondoWindow"),
    gleoPill: document.getElementById("gleoPill"),
    gleoStatusText: document.getElementById("gleoStatusText"),
    micPrivacyChip: document.getElementById("micPrivacyChip"),
    sdkVeh: document.getElementById("sdkVeh"),
    sdkLoc: document.getElementById("sdkLoc"),
    sdkNavi: document.getElementById("sdkNavi"),
    sdkStt: document.getElementById("sdkStt"),
    sdkTts: document.getElementById("sdkTts"),
    clockText: document.getElementById("clockText"),

    gearP: document.getElementById("gearP"),
    gearD: document.getElementById("gearD"),
    hazardBtn: document.getElementById("hazardBtn"),
    osHazardText: document.getElementById("osHazardText"),
    speedValue: document.getElementById("speedValue"),
    adasPill: document.getElementById("adasPill"),
    adasText: document.getElementById("adasText"),
    laneVizTitle: document.getElementById("laneVizTitle"),
    egoLaneLabel: document.getElementById("egoLaneLabel"),
    laneSlots: [
      document.getElementById("laneSlot1"),
      document.getElementById("laneSlot2"),
      document.getElementById("laneSlot3"),
      document.getElementById("laneSlot4")
    ],
    audioFocusLabel: document.getElementById("audioFocusLabel"),
    lifecycleLabel: document.getElementById("lifecycleLabel"),

    canvas: document.getElementById("highwayCanvas"),
    naviBar: document.getElementById("naviBar"),
    routeShield: document.getElementById("routeShield"),
    routeTitle: document.getElementById("routeTitle"),
    routeSub: document.getElementById("routeSub"),
    roleIndicator: document.getElementById("roleIndicator"),
    naviFooterCoord: document.getElementById("naviFooterCoord"),
    snapshotBadge: document.getElementById("snapshotBadge"),
    snapshotText: document.getElementById("snapshotText"),
    windowDivider: document.getElementById("windowDivider"),

    liveMilepost: document.getElementById("liveMilepost"),
    uxRestrictionBadge: document.getElementById("uxRestrictionBadge"),
    windowModeBtn: document.getElementById("windowModeBtn"),
    themeToggleBtn: document.getElementById("themeToggleBtn"),

    ondoPrimaryCard: document.getElementById("ondoPrimaryCard"),
    ondoStatusDot: document.getElementById("ondoStatusDot"),
    ondoStatusLabel: document.getElementById("ondoStatusLabel"),
    ondoHeadline: document.getElementById("ondoHeadline"),
    ondoDesc: document.getElementById("ondoDesc"),
    ondoVisualSlot: document.getElementById("ondoVisualSlot"),
    ondoBottomSlot: document.getElementById("ondoBottomSlot"),

    sensorHazard: document.getElementById("sensorHazard"),
    sensorDecel: document.getElementById("sensorDecel"),
    sensorTemp: document.getElementById("sensorTemp"),

    sceneBtns: document.querySelectorAll("[data-scene-btn]"),
    autoPlayBtn: document.getElementById("autoPlayBtn"),
    cleanToggleBtn: document.getElementById("cleanToggleBtn"),
    muteBtn: document.getElementById("muteBtn")
  };

  const ctx = el.canvas.getContext("2d");
  let traffic = [];

  function resetTrafficCohort(mode) {
    if (mode === "witness") {
      traffic = [
        { id: "L1_0", lane: 1, yPx: 280, speedKmh: 118, mode: "loop", color: "#64748b" },
        { id: "L1_1", lane: 1, yPx: 930, speedKmh: 118, mode: "loop", color: "#94a3b8" },
        { id: "L3_lead", lane: 3, yPx: 195, speedKmh: 102, mode: "lead", color: "#475569" },
        { id: "L4_0", lane: 4, yPx: 180, speedKmh: 66, mode: "loop", color: "#64748b" },
        { id: "L4_1", lane: 4, yPx: 830, speedKmh: 66, mode: "loop", color: "#475569" }
      ];
    } else if (mode === "rear_alert") {
      traffic = [
        { id: "L1_0", lane: 1, yPx: 320, speedKmh: 118, mode: "loop", color: "#64748b" },
        { id: "L1_1", lane: 1, yPx: 970, speedKmh: 118, mode: "loop", color: "#94a3b8" },
        { id: "L3_lead", lane: 3, yPx: 190, speedKmh: 88, mode: "lead", color: "#475569" },
        { id: "L4_0", lane: 4, yPx: 220, speedKmh: 58, mode: "loop", color: "#64748b" },
        { id: "L4_1", lane: 4, yPx: 870, speedKmh: 58, mode: "loop", color: "#475569" }
      ];
    } else {
      traffic = [
        { id: "P_bay1", lane: 1, yPx: 240, speedKmh: 0, mode: "parked", color: "#475569" }
      ];
    }
  }
  resetTrafficCohort("witness");

  function playChime(type) {
    if (!state.ttsEnabled || state.deterministicVideoMode) return;
    try {
      const AudioCtx = window.AudioContext || window.webkitAudioContext;
      const actx = new AudioCtx();
      const now = actx.currentTime;
      const osc = actx.createOscillator();
      const gain = actx.createGain();
      osc.connect(gain);
      gain.connect(actx.destination);

      if (type === "snapshot") {
        osc.type = "sine";
        osc.frequency.setValueAtTime(660, now);
        osc.frequency.exponentialRampToValueAtTime(990, now + 0.12);
        gain.gain.setValueAtTime(0.08, now);
        gain.gain.exponentialRampToValueAtTime(0.001, now + 0.24);
        osc.start(now);
        osc.stop(now + 0.25);
      } else if (type === "alert") {
        osc.type = "triangle";
        osc.frequency.setValueAtTime(587.33, now);
        osc.frequency.setValueAtTime(880.0, now + 0.14);
        gain.gain.setValueAtTime(0.1, now);
        gain.gain.exponentialRampToValueAtTime(0.001, now + 0.36);
        osc.start(now);
        osc.stop(now + 0.38);
      }
    } catch (_) {}
  }

  // Official Pleos AudioFocus DUCK + 180ms Pre-roll Silence TTS
  function speakKoreanWithPreroll(text) {
    if (!state.ttsEnabled || state.deterministicVideoMode || !("speechSynthesis" in window)) return;
    try {
      window.speechSynthesis.cancel();
      setTimeout(() => {
        const utter = new SpeechSynthesisUtterance(text);
        utter.lang = "ko-KR";
        utter.rate = 1.04;
        utter.pitch = 1.0;
        window.speechSynthesis.speak(utter);
      }, 180);
    } catch (_) {}
  }

  function clearAutoTimers() {
    state.autoTimers.forEach((id) => clearTimeout(id));
    state.autoTimers = [];
    if (state.sttTyperTimer) {
      clearInterval(state.sttTyperTimer);
      state.sttTyperTimer = null;
    }
  }

  function setActiveSdks(list) {
    const map = {
      veh: el.sdkVeh,
      loc: el.sdkLoc,
      navi: el.sdkNavi,
      stt: el.sdkStt,
      tts: el.sdkTts
    };
    Object.keys(map).forEach((k) => {
      if (map[k]) map[k].classList.toggle("active", list.includes(k));
    });
  }

  function updateStepButtons(sceneKey) {
    el.sceneBtns.forEach((btn) => {
      btn.classList.toggle("active", btn.getAttribute("data-scene-btn") === sceneKey);
    });
  }

  function setGearAndRestriction(gear) {
    state.gear = gear;
    el.gearD.classList.toggle("active", gear === "D");
    el.gearP.classList.toggle("active", gear === "P");

    // CarUxRestrictionsManager: Gear D requires distraction optimization, Gear P unlocks parking mode
    const restricted = gear !== "P";
    el.uxRestrictionBadge.textContent = restricted ? "주행안전 DO" : "주차 모드";
    el.uxRestrictionBadge.classList.toggle("restricted", restricted);
  }

  function setHazard(on) {
    state.hazardOn = on;
    el.hazardBtn.classList.toggle("blinking", on);
    el.osHazardText.textContent = on ? "비상등 점등" : "비상등 대기";
    el.sensorHazard.textContent = on ? "비상등 ON" : "비상등 대기";
    el.sensorHazard.classList.toggle("warn", on);
  }

  function bindDynamicOneTouchBtn() {
    const btn = document.getElementById("oneTouchBtn");
    if (btn) {
      btn.onclick = () => {
        if (state.deterministicVideoMode) return;
        clearAutoTimers();
        applyScene("stt_snapshot");
        state.autoTimers.push(
          setTimeout(() => {
            if (state.scene === "stt_snapshot") applyScene("cross_verified");
          }, 4800)
        );
      };
    }
  }

  function applyScene(scene) {
    const prevScene = state.scene;
    state.scene = scene;
    updateStepButtons(scene);
    if (state.sttTyperTimer) {
      clearInterval(state.sttTyperTimer);
      state.sttTyperTimer = null;
    }

    if (scene === "driving") {
      setGearAndRestriction("D");
      state.speedKmh = 102;
      state.targetSpeedKmh = 102;
      state.decelMps2 = -0.2;
      setHazard(false);
      state.lockedMilepostKm = null;
      state.liveMilepostKm = 342.33;
      state.egoLane = 3.0;
      state.targetEgoLane = 3.0;
      state.accStartScreenY = DEFAULT_ACC_START_Y;
      state.accSpawnScrollPx = state.worldScrollPx;
      state.accActive = true;
      resetTrafficCohort("witness");

      el.gleoPill.classList.remove("listening");
      el.gleoStatusText.textContent = "Gleo AI · Schema Ready";
      el.micPrivacyChip.classList.remove("visible");
      el.audioFocusLabel.textContent = "STANDBY (180ms Pre-roll)";
      el.audioFocusLabel.classList.remove("ducking");

      el.adasPill.classList.remove("warn");
      el.adasText.textContent = "HDA2 고속도로 주행 보조 · 3차로";

      el.snapshotBadge.classList.remove("visible");
      el.naviBar.classList.remove("alert-mode");
      el.routeShield.textContent = "경부 1";
      el.routeTitle.textContent = "경부고속도로 하행 (부산·대전 방면)";
      el.routeSub.textContent = "NaviHelper 경로 연동 · 전방 안전 모니터링 중";
      el.roleIndicator.textContent = "선행 목격 차량";

      el.ondoPrimaryCard.classList.remove("alert-border");
      el.ondoStatusDot.style.background = "var(--pleos-active)";
      el.ondoStatusLabel.textContent = "전방 고속도로 안전 모니터링";
      el.ondoHeadline.textContent = "도로 위 위험 목격 시 원터치로 즉시 제보하세요";
      el.ondoDesc.className = "ondo-stage-desc";
      el.ondoDesc.textContent = "터치 즉시 현재 이정(km)이 t=0으로 고정되며 3초 핸즈프리 음성 제보로 연결됩니다.";
      el.ondoVisualSlot.innerHTML = `
        <div class="visual-pipeline-grid">
          <div class="vp-step">
            <span class="vp-num">STEP 01 · t=0 고정</span>
            <strong class="vp-title">정밀 이정 스냅샷</strong>
            <span class="vp-sub">100km/h 주행 오차 차단</span>
          </div>
          <div class="vp-step">
            <span class="vp-num">STEP 02 · 핸즈프리</span>
            <strong class="vp-title">3초 음성 · 센서 검증</strong>
            <span class="vp-sub">Gleo STT + 비상등·감속</span>
          </div>
          <div class="vp-step">
            <span class="vp-num">STEP 03 · 36µs 중계</span>
            <strong class="vp-title">후방 3km 즉시 전파</strong>
            <span class="vp-sub">도로공사 · 후방 38대</span>
          </div>
        </div>
      `;
      el.ondoBottomSlot.innerHTML = `
        <button type="button" class="pleos-large-cta" id="oneTouchBtn">
          <span class="cta-ring-badge">ON</span>
          <span>도로 위 사고 원터치 제보 (t=0 이정 고정)</span>
        </button>
      `;
      bindDynamicOneTouchBtn();
      setActiveSdks(["veh", "loc", "navi"]);
    }

    if (scene === "stt_snapshot") {
      playChime("snapshot");
      setGearAndRestriction("D");
      state.targetSpeedKmh = 78;
      state.decelMps2 = -3.4;
      setHazard(true);
      state.lockedMilepostKm = 342.5;
      if (state.liveMilepostKm < 342.5) state.liveMilepostKm = 342.5;
      state.egoLane = 3.0;
      state.targetEgoLane = 3.0;
      if (prevScene === "rear_alert" || prevScene === "arrival_summary") {
        resetTrafficCohort("witness");
        state.accStartScreenY = 620;
        state.accSpawnScrollPx = state.worldScrollPx;
      }
      state.accActive = true;

      el.gleoPill.classList.add("listening");
      el.gleoStatusText.textContent = "Gleo AI · REPORT_HIGHWAY_INCIDENT";
      el.micPrivacyChip.classList.add("visible");
      el.audioFocusLabel.textContent = "DUCKING · STT/TTS +180ms";
      el.audioFocusLabel.classList.add("ducking");

      el.adasPill.classList.add("warn");
      el.adasText.textContent = "비상감속 감지 · 안전 차간거리 확보";

      el.snapshotBadge.classList.add("visible");
      el.snapshotText.textContent = "t=0 고정: 342.5km";
      el.naviBar.classList.remove("alert-mode");
      el.routeShield.textContent = "경부 1";
      el.routeTitle.textContent = "사고 목격 지점 t=0 스냅샷 고정 완료";
      el.routeSub.textContent = "차량이 계속 이동해도 신고 좌표는 목격 지점(342.5km)으로 유지됩니다";
      el.roleIndicator.textContent = "1단계 · 음성 제보";

      el.ondoPrimaryCard.classList.remove("alert-border");
      el.ondoStatusDot.style.background = "var(--ondo-warm)";
      el.ondoStatusLabel.textContent = "1단계 · t=0 위치 스냅샷 고정 완료 (0.8초)";
      el.ondoHeadline.textContent = "경부선 하행 342.5km 고정됨 — 상황을 말씀해 주세요";
      el.ondoDesc.className = "ondo-stage-desc";
      el.ondoDesc.textContent = "핸즈프리 음성 인식 중입니다. 차로와 위험 유형만 짧게 말씀해 주세요.";
      el.ondoVisualSlot.innerHTML = `
        <div class="visual-lock-compare">
          <div class="vlc-box">
            <span class="vlc-label">신고 패킷 고정 좌표 (t=0 스냅샷)</span>
            <strong class="vlc-val">경부선 하행 342.50km</strong>
            <span class="vlc-note">✓ 방위각 168° · 목격 지점 영구 고정</span>
          </div>
          <div class="vlc-box right">
            <span class="vlc-label">현재 주행 통과 위치</span>
            <strong class="vlc-val secondary" id="liveDriftVal">342.50km (+0m)</strong>
            <span class="vlc-label">음성 발화 중 주행 거리 보정됨</span>
          </div>
        </div>
      `;
      el.ondoBottomSlot.innerHTML = `
        <div class="pleos-stt-surface">
          <div class="stt-live-row">
            <span class="stt-transcript-text" id="detTranscript">""</span>
            <div class="stt-waveform" id="detWave" aria-hidden="true">
              <span class="stt-wave-bar"></span>
              <span class="stt-wave-bar"></span>
              <span class="stt-wave-bar"></span>
              <span class="stt-wave-bar"></span>
              <span class="stt-wave-bar"></span>
            </div>
          </div>
          <div class="nlu-slot-chips" id="detChips">
            <span class="nlu-chip">이정: 경부선 하행 342.5km</span>
            <span class="nlu-chip">차로: 2차로</span>
            <span class="nlu-chip">유형: 화물차 전복</span>
            <span class="nlu-chip gleo">Gleo Schema 매칭</span>
          </div>
        </div>
      `;
      setActiveSdks(["veh", "loc", "stt", "tts"]);

      if (!state.deterministicVideoMode) {
        const fullStr = "2차로 화물차 전복, 파편 주의";
        const trEl = document.getElementById("detTranscript");
        let idx = 0;
        state.sttTyperTimer = setInterval(() => {
          if (state.scene !== "stt_snapshot" || !trEl) {
            clearInterval(state.sttTyperTimer);
            return;
          }
          idx = Math.min(fullStr.length, idx + 1);
          trEl.textContent = `"${fullStr.slice(0, idx)}"`;
          if (idx >= fullStr.length) clearInterval(state.sttTyperTimer);
        }, 75);
        speakKoreanWithPreroll("삼백사십이점 오 킬로미터 지점이 고정되었습니다. 상황을 말씀해 주세요.");
      }
    }

    if (scene === "cross_verified") {
      playChime("snapshot");
      setGearAndRestriction("D");
      state.targetSpeedKmh = 82;
      state.decelMps2 = -3.4;
      setHazard(true);
      state.lockedMilepostKm = 342.5;
      if (prevScene === "rear_alert" || prevScene === "arrival_summary") {
        resetTrafficCohort("witness");
        state.accActive = false;
      }

      el.gleoPill.classList.remove("listening");
      el.gleoStatusText.textContent = "Gleo AI · Validated (98%)";
      el.micPrivacyChip.classList.remove("visible");
      el.audioFocusLabel.textContent = "DUCKING · +180ms Pre-roll TTS";
      el.audioFocusLabel.classList.add("ducking");

      el.adasPill.classList.remove("warn");
      el.adasText.textContent = "V2X 릴레이 송출 완료 · 3차로 정속 복귀";

      el.snapshotBadge.classList.add("visible");
      el.snapshotText.textContent = "관제 접수: 342.5km · 2차로";
      el.naviBar.classList.remove("alert-mode");
      el.routeShield.textContent = "경부 1";
      el.routeTitle.textContent = "한국도로공사(ex) 및 119 긴급 패킷 전송 완료";
      el.routeSub.textContent = "Vehicle SDK 교차 검증 통과 → 무GC Rust 서버(36µs) 후방 전파";
      el.roleIndicator.textContent = "2단계 · 검증 완료";

      el.ondoPrimaryCard.classList.remove("alert-border");
      el.ondoStatusDot.style.background = "var(--pleos-active)";
      el.ondoStatusLabel.textContent = "2단계 · 센서 교차 검증 완료 (신뢰도 98%)";
      el.ondoHeadline.textContent = "도로공사·119 접수 및 후방 3km 전파 완료";
      el.ondoDesc.className = "ondo-stage-desc highlight-green";
      el.ondoDesc.textContent = "경부선 하행 342.5km · 2차로 화물차 전복 (Rust V2X 중계 지연 36µs)";
      el.ondoVisualSlot.innerHTML = `
        <div class="visual-impact-stats">
          <div class="vis-stat">
            <span class="vis-label">교차 검증 신뢰도</span>
            <strong class="vis-num green">98%</strong>
            <span class="vp-sub">센서 + 다중차량 일치</span>
          </div>
          <div class="vis-stat">
            <span class="vis-label">Rust V2X 중계 지연</span>
            <strong class="vis-num warm">36 µs</strong>
            <span class="vp-sub">Zero-GC 실시간 브로드캐스트</span>
          </div>
          <div class="vis-stat">
            <span class="vis-label">동시 전파 대상</span>
            <strong class="vis-num">ex · 38대</strong>
            <span class="vp-sub">도로공사 상황실 & 후방차량</span>
          </div>
        </div>
      `;
      el.ondoBottomSlot.innerHTML = `
        <div class="pleos-verify-surface">
          <div class="verify-check-row">
            <span><strong class="verify-check-mark">✓</strong>Vehicle SDK 비상경고등 ON · 급감속 -3.4 m/s²</span>
            <span class="verify-check-tag">센서 일치</span>
          </div>
          <div class="verify-check-row">
            <span><strong class="verify-check-mark">✓</strong>Pleos Cloud 후속 차량 다중 감지 (+1대)</span>
            <span class="verify-check-tag">교차 검증</span>
          </div>
          <div class="verify-check-row">
            <span><strong class="verify-check-mark">✓</strong>도로공사 상황실 접수 · 후방 38대 즉시 경고</span>
            <span class="verify-check-tag">송출 완료</span>
          </div>
        </div>
      `;
      setActiveSdks(["veh", "loc", "navi", "tts"]);
      speakKoreanWithPreroll("센서 교차 검증이 완료되어 도로공사 상황실과 후방 차량에 즉시 전파했습니다.");
    }

    if (scene === "rear_alert") {
      playChime("alert");
      setGearAndRestriction("D");
      state.speedKmh = 104;
      state.targetSpeedKmh = 72;
      state.decelMps2 = -2.1;
      setHazard(true);
      state.lockedMilepostKm = null;
      state.liveMilepostKm = 340.2;
      state.rearAlertDistKm = 2.3;
      state.egoLane = 2.0;
      state.targetEgoLane = 3.0;
      state.accActive = false;
      resetTrafficCohort("rear_alert");

      el.gleoPill.classList.remove("listening");
      el.gleoStatusText.textContent = "Gleo AI · CHECK_AHEAD_HAZARDS";
      el.micPrivacyChip.classList.remove("visible");
      el.audioFocusLabel.textContent = "DUCKING · 긴급 TTS 송출 중";
      el.audioFocusLabel.classList.add("ducking");

      el.adasPill.classList.add("warn");
      el.adasText.textContent = "전방 2차로 통제 · 우측 3차로 회피 유도";

      el.snapshotBadge.classList.remove("visible");
      el.naviBar.classList.add("alert-mode");
      el.routeShield.textContent = "긴급 주의";
      el.routeTitle.textContent = "전방 2.3km · 2차로 화물차 전복 사고 (검증 제보)";
      el.routeSub.textContent = "NaviHelper 주행 경로 일치 · 미리 우측 3·4차로로 변경해 서행하세요";
      el.roleIndicator.textContent = "후방 2.3km 차량";

      el.ondoPrimaryCard.classList.add("alert-border");
      el.ondoStatusDot.style.background = "var(--pleos-error)";
      el.ondoStatusLabel.textContent = "2차 사고 예방 긴급 경보 · NaviHelper + TTS";
      el.ondoHeadline.textContent = "전방 2.3km · 2차로 화물차 전복 발생!";
      el.ondoDesc.className = "ondo-stage-desc highlight-warm";
      el.ondoDesc.textContent = "미리 우측 3·4차로로 차로를 변경하고 70km/h 이하로 서행하세요.";
      el.ondoVisualSlot.innerHTML = `
        <div class="visual-lane-guide">
          <div class="vlg-lane">
            <span class="vlg-name">1차로</span>
            <span class="vlg-icon">↑</span>
            <span class="vlg-status">주의 통과</span>
          </div>
          <div class="vlg-lane danger">
            <span class="vlg-name">2차로</span>
            <span class="vlg-icon">✕</span>
            <span class="vlg-status">전복 통제</span>
          </div>
          <div class="vlg-lane recommend">
            <span class="vlg-name">3차로</span>
            <span class="vlg-icon">↗</span>
            <span class="vlg-status">권장 회피</span>
          </div>
          <div class="vlg-lane recommend">
            <span class="vlg-name">4차로</span>
            <span class="vlg-icon">↑</span>
            <span class="vlg-status">안전 서행</span>
          </div>
        </div>
      `;
      el.ondoBottomSlot.innerHTML = `
        <div class="pleos-alert-surface">
          <span class="alert-tts-text">주행 경로 일치 확인됨 · 180ms 선행묵음 TTS 음성 안내 송출 중</span>
          <span class="alert-preroll-pill">2차 추돌 차단</span>
        </div>
      `;
      setActiveSdks(["veh", "navi", "tts"]);
      speakKoreanWithPreroll("전방 이점 삼 킬로미터, 이차로에 화물차 전복 사고 제보가 접수되었습니다. 미리 우측 차선으로 이동해 서행하세요.");
    }

    if (scene === "arrival_summary") {
      playChime("snapshot");
      setGearAndRestriction("P");
      state.speedKmh = 0;
      state.targetSpeedKmh = 0;
      state.decelMps2 = 0.0;
      setHazard(false);
      state.lockedMilepostKm = null;
      state.egoLane = 3.0;
      state.targetEgoLane = 3.0;
      state.accActive = false;
      resetTrafficCohort("arrival");

      el.gleoPill.classList.remove("listening");
      el.gleoStatusText.textContent = "Gleo AI · Safety Summary";
      el.micPrivacyChip.classList.remove("visible");
      el.audioFocusLabel.textContent = "STANDBY · 기어 P 브리핑 완료";
      el.audioFocusLabel.classList.remove("ducking");

      el.adasPill.classList.remove("warn");
      el.adasText.textContent = "주차 구획 정차 완료 (Gear P · 0 km/h)";

      el.snapshotBadge.classList.remove("visible");
      el.naviBar.classList.remove("alert-mode");
      el.routeShield.textContent = "주행 종료";
      el.routeTitle.textContent = "목적지 주차장 정차 완료 · ON:DO 세이프티 리포트";
      el.routeSub.textContent = "누군가의 삶을 지켜온 온도가 과거에서 현재로, 미래까지 이어집니다";
      el.roleIndicator.textContent = "주차(P) 리포트";

      el.ondoPrimaryCard.classList.remove("alert-border");
      el.ondoStatusDot.style.background = "var(--ondo-warm)";
      el.ondoStatusLabel.textContent = "주행 종료(P) · 36.5°C 세이프티 리포트";
      el.ondoHeadline.textContent = "당신의 36.5°C 온도가 후방 38대를 지켰습니다";
      el.ondoDesc.className = "ondo-stage-desc";
      el.ondoDesc.textContent = "실시간 정밀 제보 덕분에 뒤따르던 차량들이 안전하게 차로를 변경하고 감속했습니다.";
      el.ondoVisualSlot.innerHTML = `
        <div class="visual-impact-stats">
          <div class="vis-stat">
            <span class="vis-label">보호한 후방 차량</span>
            <strong class="vis-num green">38대</strong>
            <span class="vp-sub">전원 안전 차로변경·감속</span>
          </div>
          <div class="vis-stat">
            <span class="vis-label">온도 릴레이 반경</span>
            <strong class="vis-num">3.0 km</strong>
            <span class="vp-sub">경부선 하행 342.5km 기점</span>
          </div>
          <div class="vis-stat">
            <span class="vis-label">적립 안전 리워드</span>
            <strong class="vis-num warm">+500 P</strong>
            <span class="vp-sub">블루멤버스 · 안전 마일리지</span>
          </div>
        </div>
      `;
      el.ondoBottomSlot.innerHTML = `
        <div class="pleos-warmth-surface">
          <div>
            <div class="warmth-reward-title">+500P 블루멤버스 · 도로공사 안전 마일리지 적립 완료</div>
            <div class="warmth-reward-sub">총 38대 후방 접근 차량 2차 추돌 예방 확인됨</div>
          </div>
          <div class="warmth-temp-hero">36.5°C</div>
        </div>
      `;
      setActiveSdks(["veh", "tts"]);
      speakKoreanWithPreroll("주행이 종료되었습니다. 오늘 당신이 켠 삼십육점 오 도 온도가 뒤따르던 서른여덟 대의 차량을 안전하게 지켰습니다.");
    }
  }

  function resizeCanvas() {
    if (state.isLandscape20) return;
    const rect = el.canvas.parentElement.getBoundingClientRect();
    const dpr = state.deterministicVideoMode ? 1 : (window.devicePixelRatio || 1);
    el.canvas.width = Math.max(1, Math.floor(rect.width * dpr));
    el.canvas.height = Math.max(1, Math.floor(rect.height * dpr));
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  }

  function aabbIntersects(a, b, pad) {
    const p = pad || 0;
    return !(
      a.x2 + p <= b.x1 - p ||
      a.x1 - p >= b.x2 + p ||
      a.y2 + p <= b.y1 - p ||
      a.y1 - p >= b.y2 + p
    );
  }

  function drawCarTopView(x, y, w, h, bodyColor, isEgo, hazardBlinkOn, isParked) {
    ctx.save();
    ctx.translate(x, y);

    ctx.fillStyle = "rgba(0, 0, 0, 0.45)";
    ctx.beginPath();
    ctx.roundRect(-w / 2 - 2, -h / 2 + 4, w + 4, h + 4, 8);
    ctx.fill();

    ctx.fillStyle = bodyColor;
    ctx.beginPath();
    ctx.roundRect(-w / 2, -h / 2, w, h, 8);
    ctx.fill();

    ctx.fillStyle = "#0f172a";
    ctx.beginPath();
    ctx.roundRect(-w * 0.38, -h * 0.24, w * 0.76, h * 0.5, 5);
    ctx.fill();

    if (isEgo && !isParked) {
      const grad = ctx.createLinearGradient(0, -h / 2, 0, -h / 2 - 100);
      grad.addColorStop(0, "rgba(226, 232, 240, 0.24)");
      grad.addColorStop(1, "rgba(226, 232, 240, 0)");
      ctx.fillStyle = grad;
      ctx.beginPath();
      ctx.moveTo(-w * 0.42, -h / 2);
      ctx.lineTo(-w * 1.18, -h / 2 - 100);
      ctx.lineTo(w * 1.18, -h / 2 - 100);
      ctx.lineTo(w * 0.42, -h / 2);
      ctx.closePath();
      ctx.fill();
    }

    if (isEgo && isParked) {
      // Stationary parking/tail brake lights (no highway headlight beams in Gear P)
      ctx.fillStyle = "#ef4444";
      ctx.beginPath();
      ctx.roundRect(-w / 2 + 3, h / 2 - 4, 7, 3, 1.5);
      ctx.roundRect(w / 2 - 10, h / 2 - 4, 7, 3, 1.5);
      ctx.fill();
    }

    if (hazardBlinkOn) {
      ctx.fillStyle = "#f59e0b";
      ctx.beginPath();
      ctx.arc(-w / 2 + 3, -h / 2 + 4, 4.2, 0, Math.PI * 2);
      ctx.arc(w / 2 - 3, -h / 2 + 4, 4.2, 0, Math.PI * 2);
      ctx.arc(-w / 2 + 3, h / 2 - 4, 4.2, 0, Math.PI * 2);
      ctx.arc(w / 2 - 3, h / 2 - 4, 4.2, 0, Math.PI * 2);
      ctx.fill();
    }

    ctx.restore();
  }

  function drawParkingLotCanvas(w, h, animSec) {
    ctx.fillStyle = "#090d14";
    ctx.fillRect(0, 0, w, h);

    const courtW = Math.min(w - 36, 484);
    const courtL = (w - courtW) / 2;
    const courtT = Math.max(106, Math.round(h * 0.16));
    const courtB = Math.min(h - 70, Math.round(h * 0.87));
    const courtH = Math.max(360, courtB - courtT);

    // Paved concrete parking lot surface
    ctx.fillStyle = "#131b26";
    ctx.strokeStyle = "#2b3648";
    ctx.lineWidth = 2;
    ctx.beginPath();
    ctx.roundRect(courtL, courtT, courtW, courtH, 16);
    ctx.fill();
    ctx.stroke();

    // Top pedestrian walkway & zebra stripes (Rest Area / Destination Plaza)
    const walkH = 38;
    ctx.fillStyle = "#1a2433";
    ctx.beginPath();
    ctx.roundRect(courtL + 8, courtT + 8, courtW - 16, walkH, 10);
    ctx.fill();

    ctx.fillStyle = "rgba(226, 232, 240, 0.22)";
    for (let sx = courtL + 22; sx < courtL + 116; sx += 14) {
      ctx.fillRect(sx, courtT + 14, 7, walkH - 12);
    }

    ctx.fillStyle = "#94a3b8";
    ctx.font = '700 12px "Asta Sans", sans-serif';
    ctx.textAlign = "right";
    ctx.fillText("목적지 휴게소 보행자 통로 · 주차구역", courtL + courtW - 20, courtT + 31);

    const padX = 22;
    const baysW = courtW - padX * 2;
    const stallW = baysW / 4;
    const stallH = Math.min(162, Math.max(108, courtH * 0.30));

    const rowATop = courtT + walkH + 22;
    const rowABot = rowATop + stallH;
    const rowBBot = courtB - 22;
    const rowBTop = rowBBot - stallH;
    const aisleMidY = (rowABot + rowBTop) / 2;

    // Upper Row A (A-01 .. A-04) U-shaped white parking bay lines + wheel stoppers
    ctx.strokeStyle = "rgba(226, 232, 240, 0.78)";
    ctx.lineWidth = 2.5;
    ctx.beginPath();
    ctx.moveTo(courtL + padX, rowATop);
    ctx.lineTo(courtL + padX + baysW, rowATop);
    for (let i = 0; i <= 4; i++) {
      const bx = courtL + padX + stallW * i;
      ctx.moveTo(bx, rowATop);
      ctx.lineTo(bx, rowABot);
    }
    ctx.stroke();

    for (let i = 0; i < 4; i++) {
      const cx = courtL + padX + stallW * (i + 0.5);
      // Wheel stopper (카스토퍼)
      ctx.fillStyle = "#475569";
      ctx.beginPath();
      ctx.roundRect(cx - 20, rowATop + 10, 40, 6, 3);
      ctx.fill();
      ctx.fillStyle = "#f59e0b";
      ctx.fillRect(cx - 14, rowATop + 11.5, 8, 3);
      ctx.fillRect(cx + 6, rowATop + 11.5, 8, 3);

      // Bay label
      ctx.fillStyle = "rgba(148, 163, 184, 0.62)";
      ctx.font = '700 11.5px "Asta Sans", sans-serif';
      ctx.textAlign = "center";
      ctx.fillText(`A-0${i + 1}`, cx, rowABot - 10);

      if (i === 1 || i === 2) {
        ctx.fillStyle = "rgba(148, 163, 184, 0.22)";
        ctx.font = '800 22px "Asta Sans", sans-serif';
        ctx.fillText("P", cx, (rowATop + rowABot) / 2 + 8);
      }
    }

    // Central Parking Driveway Aisle (0 km/h calm zone)
    ctx.strokeStyle = "rgba(148, 163, 184, 0.22)";
    ctx.lineWidth = 1.5;
    ctx.setLineDash([12, 12]);
    ctx.beginPath();
    ctx.moveTo(courtL + padX + 8, aisleMidY);
    ctx.lineTo(courtL + padX + baysW - 8, aisleMidY);
    ctx.stroke();
    ctx.setLineDash([]);

    // Central P Lot Badge on pavement
    const pPillW = 216;
    const pPillH = 34;
    ctx.fillStyle = "rgba(15, 23, 42, 0.92)";
    ctx.strokeStyle = "rgba(56, 189, 248, 0.42)";
    ctx.lineWidth = 1.4;
    ctx.beginPath();
    ctx.roundRect(w / 2 - pPillW / 2, aisleMidY - pPillH / 2, pPillW, pPillH, 999);
    ctx.fill();
    ctx.stroke();

    ctx.fillStyle = "#e2e8f0";
    ctx.font = '800 12.5px "Asta Sans", sans-serif';
    ctx.textAlign = "center";
    ctx.fillText("P  주차장 구역 · 정차 완료 (0 km/h)", w / 2, aisleMidY + 4.5);

    // Lower Row B (B-01 .. B-04) U-shaped white parking bay lines + wheel stoppers
    const egoBayIdx = 2; // B-03
    const egoBayLeft = courtL + padX + stallW * egoBayIdx;
    const pulse = (Math.sin(animSec * 3.0) + 1) / 2;

    // Highlight active Ego Parked Bay (B-03)
    ctx.fillStyle = `rgba(0, 130, 255, ${0.14 + pulse * 0.08})`;
    ctx.fillRect(egoBayLeft + 2, rowBTop + 2, stallW - 4, stallH - 4);

    ctx.strokeStyle = "rgba(226, 232, 240, 0.78)";
    ctx.lineWidth = 2.5;
    ctx.beginPath();
    ctx.moveTo(courtL + padX, rowBBot);
    ctx.lineTo(courtL + padX + baysW, rowBBot);
    for (let i = 0; i <= 4; i++) {
      const bx = courtL + padX + stallW * i;
      ctx.moveTo(bx, rowBTop);
      ctx.lineTo(bx, rowBBot);
    }
    ctx.stroke();

    // Crisp active blue border around B-03
    ctx.strokeStyle = "#0082ff";
    ctx.lineWidth = 2.8;
    ctx.strokeRect(egoBayLeft + 2, rowBTop + 2, stallW - 4, stallH - 4);

    for (let i = 0; i < 4; i++) {
      const cx = courtL + padX + stallW * (i + 0.5);
      // Wheel stopper at bottom of Row B
      ctx.fillStyle = "#475569";
      ctx.beginPath();
      ctx.roundRect(cx - 20, rowBBot - 16, 40, 6, 3);
      ctx.fill();
      ctx.fillStyle = "#f59e0b";
      ctx.fillRect(cx - 14, rowBBot - 14.5, 8, 3);
      ctx.fillRect(cx + 6, rowBBot - 14.5, 8, 3);

      ctx.fillStyle = i === egoBayIdx ? "#60a5fa" : "rgba(148, 163, 184, 0.62)";
      ctx.font = '800 11.5px "Asta Sans", sans-serif';
      ctx.textAlign = "center";
      ctx.fillText(i === egoBayIdx ? "B-03 · 정차" : `B-0${i + 1}`, cx, rowBTop + 16);

      if (i === 1 || i === 3) {
        ctx.fillStyle = "rgba(148, 163, 184, 0.22)";
        ctx.font = '800 22px "Asta Sans", sans-serif';
        ctx.fillText("P", cx, (rowBTop + rowBBot) / 2 + 6);
      }
    }

    // Stationary Parked Neighbor Cars (0 km/h, zero motion)
    const rowACarY = (rowATop + rowABot) / 2 + 4;
    const rowBCarY = (rowBTop + rowBBot) / 2 + 2;
    const parkedNeighbors = [
      { id: "parked_A1", bay: 0, cy: rowACarY, color: "#475569" },
      { id: "parked_A4", bay: 3, cy: rowACarY, color: "#64748b" },
      { id: "parked_B1", bay: 0, cy: rowBCarY, color: "#334155" }
    ];

    const egoX = courtL + padX + stallW * (egoBayIdx + 0.5);
    const egoY = rowBCarY;
    const placedBoxes = [
      {
        id: "ego",
        lane: 3.0,
        cx: egoX,
        cy: egoY,
        x1: egoX - 15,
        y1: egoY - 28,
        x2: egoX + 15,
        y2: egoY + 28
      }
    ];

    parkedNeighbors.forEach((p) => {
      const cx = courtL + padX + stallW * (p.bay + 0.5);
      placedBoxes.push({
        id: p.id,
        lane: p.bay + 1,
        cx: cx,
        cy: p.cy,
        x1: cx - 13,
        y1: p.cy - 24,
        x2: cx + 13,
        y2: p.cy + 24
      });
      drawCarTopView(cx, p.cy, 26, 48, p.color, false, false, true);
    });

    // Draw Stationary Ego Vehicle cleanly inside Parking Bay B-03
    drawCarTopView(egoX, egoY, 30, 56, "#e2e8f0", true, false, true);

    return {
      egoY: egoY,
      boxes: placedBoxes
    };
  }

  function stepSimulationAndDraw(dt, animSec) {
    // Smooth speed & lane kinematics
    if (state.gear === "P") {
      state.speedKmh = 0;
    } else {
      state.speedKmh += (state.targetSpeedKmh - state.speedKmh) * Math.min(1, dt * 2.5);
    }
    state.egoLane += (state.targetEgoLane - state.egoLane) * Math.min(1, dt * 1.65);

    const mps = state.gear === "D" ? state.speedKmh / 3.6 : 0.0;
    const dRoadPx = mps * PX_PER_METER * dt;
    state.worldScrollPx += dRoadPx;
    state.roadOffset = ((state.worldScrollPx % 80) + 80) % 80;

    if (state.gear === "D") {
      state.liveMilepostKm += (mps * dt) / 1000.0;
      if (state.scene === "rear_alert") {
        state.rearAlertDistKm = Math.max(1.8, 342.5 - state.liveMilepostKm);
        el.routeTitle.textContent = `전방 ${state.rearAlertDistKm.toFixed(1)}km · 2차로 화물차 전복 사고 (검증 제보)`;
        el.ondoHeadline.textContent = `전방 ${state.rearAlertDistKm.toFixed(1)}km · 2차로 화물차 전복 발생!`;
      }
    }

    // OS Cluster & ON:DO Header Telemetry Updates
    el.speedValue.textContent = String(Math.round(state.speedKmh));
    if (state.gear === "P") {
      el.liveMilepost.textContent = "목적지 주차장 정차 완료 (Gear P)";
      el.naviFooterCoord.textContent = "FusedLocation · 목적지 주차구역 B-03 정차 완료 (Gear P · 0 km/h)";
    } else if (state.lockedMilepostKm !== null) {
      el.liveMilepost.textContent = `경부선 하행 ${state.lockedMilepostKm.toFixed(1)}km (t=0 고정 · 현재 ${state.liveMilepostKm.toFixed(2)}km)`;
      el.naviFooterCoord.textContent = `FusedLocation · 경부선 하행 ${state.liveMilepostKm.toFixed(2)}km (방위각 168°)`;
      const driftEl = document.getElementById("liveDriftVal");
      if (driftEl) {
        const passedM = Math.max(0, Math.round((state.liveMilepostKm - state.lockedMilepostKm) * 1000));
        driftEl.textContent = `${state.liveMilepostKm.toFixed(2)}km (+${passedM}m)`;
      }
    } else {
      el.liveMilepost.textContent = `경부선 하행 ${state.liveMilepostKm.toFixed(2)}km`;
      el.naviFooterCoord.textContent = `FusedLocation · 경부선 하행 ${state.liveMilepostKm.toFixed(2)}km (방위각 168°)`;
    }

    el.sensorDecel.textContent = `${state.decelMps2.toFixed(1)} m/s²`;
    el.sensorDecel.classList.toggle("warn", state.decelMps2 <= -2.0);
    el.sensorTemp.textContent = `외기 ${state.ambientTempC.toFixed(1)}°C`;

    const activeLaneInt = Math.min(4, Math.max(1, Math.round(state.egoLane)));
    if (state.gear === "P") {
      if (el.laneVizTitle) el.laneVizTitle.textContent = "주차 구획 상태";
      el.egoLaneLabel.textContent = "B-03 구획 정차";
      const bayNames = ["B-01", "B-02", "B-03(P)", "B-04"];
      el.laneSlots.forEach((slot, idx) => {
        slot.textContent = bayNames[idx];
        slot.classList.toggle("active", idx === 2);
        slot.classList.remove("blocked");
      });
    } else {
      if (el.laneVizTitle) el.laneVizTitle.textContent = "주행 차로 가이드";
      el.egoLaneLabel.textContent = `${activeLaneInt}차로 주행 중`;
      el.laneSlots.forEach((slot, idx) => {
        const laneNum = idx + 1;
        slot.textContent = `${laneNum}차로`;
        slot.classList.toggle("active", laneNum === activeLaneInt);
        slot.classList.toggle("blocked", laneNum === 2 && (state.scene === "rear_alert" || state.scene === "stt_snapshot"));
      });
    }

    const blinkOn = state.hazardOn && Math.floor(animSec * 2.6) % 2 === 0;
    el.hazardBtn.style.opacity = state.hazardOn ? (blinkOn ? "1" : "0.52") : "1";

    if (state.isLandscape20) {
      return {
        t: animSec,
        stage: state.scene,
        canvasW: 0,
        canvasH: 0,
        egoSpeedKmh: state.speedKmh,
        egoY: 0,
        worldScrollPx: state.worldScrollPx,
        roadOffset: state.roadOffset,
        accActive: false,
        accVisible: false,
        accX: null,
        accY: null,
        boxes: []
      };
    }

    // Draw Pleos Navigation Canvas: Parking Lot View in Gear P, or 4-Lane Highway in Gear D
    const w = state.deterministicVideoMode ? el.canvas.width : el.canvas.clientWidth;
    const h = state.deterministicVideoMode ? el.canvas.height : el.canvas.clientHeight;
    ctx.clearRect(0, 0, w, h);

    if (state.gear === "P") {
      const pLot = drawParkingLotCanvas(w, h, animSec);
      const pDiag = {
        t: animSec,
        stage: state.scene,
        canvasW: w,
        canvasH: h,
        egoSpeedKmh: 0,
        egoY: pLot.egoY,
        worldScrollPx: state.worldScrollPx,
        roadOffset: state.roadOffset,
        accActive: false,
        accVisible: false,
        accX: null,
        accY: null,
        boxes: pLot.boxes
      };
      window.__lastFrameDiagnostics = pDiag;
      return pDiag;
    }

    ctx.fillStyle = "#080c13";
    ctx.fillRect(0, 0, w, h);

    const rw = Math.min(w * 0.68, 440);
    const rl = (w - rw) / 2;
    const lw = rw / 4;

    // Asphalt surface
    ctx.fillStyle = "#121824";
    ctx.fillRect(rl, 0, rw, h);

    // Highway shoulders
    ctx.strokeStyle = "#475569";
    ctx.lineWidth = 3;
    ctx.beginPath();
    ctx.moveTo(rl, 0);
    ctx.lineTo(rl, h);
    ctx.moveTo(rl + rw, 0);
    ctx.lineTo(rl + rw, h);
    ctx.stroke();

    // Median barrier (Left)
    ctx.strokeStyle = "#f59e0b";
    ctx.lineWidth = 2;
    ctx.beginPath();
    ctx.moveTo(rl + 5, 0);
    ctx.lineTo(rl + 5, h);
    ctx.stroke();

    // Blocked Lane 2 corridor highlight in Rear Alert
    if (state.scene === "rear_alert") {
      const dangerGrad = ctx.createLinearGradient(0, 0, 0, h * 0.78);
      dangerGrad.addColorStop(0, "rgba(246, 46, 36, 0.28)");
      dangerGrad.addColorStop(1, "rgba(246, 46, 36, 0.02)");
      ctx.fillStyle = dangerGrad;
      ctx.fillRect(rl + lw, 0, lw, h * 0.78);
    }

    // Dashed lane dividers scrolling at exact dRoadPx
    ctx.strokeStyle = "rgba(203, 213, 225, 0.28)";
    ctx.lineWidth = 2;
    ctx.setLineDash([34, 46]);
    ctx.lineDashOffset = -state.roadOffset;
    for (let i = 1; i < 4; i++) {
      ctx.beginPath();
      ctx.moveTo(rl + lw * i, 0);
      ctx.lineTo(rl + lw * i, h);
      ctx.stroke();
    }
    ctx.setLineDash([]);

    // Lane watermarks
    ctx.fillStyle = "rgba(148, 163, 184, 0.24)";
    ctx.font = '700 12px "Asta Sans", sans-serif';
    ctx.textAlign = "center";
    for (let i = 0; i < 4; i++) {
      ctx.fillText(`${i + 1}차로`, rl + lw * (i + 0.5), h * 0.50);
    }

    const egoX = rl + lw * (state.egoLane - 0.5);
    const egoY = h * 0.62;
    const egoBox = {
      id: "ego",
      lane: state.egoLane,
      cx: egoX,
      cy: egoY,
      x1: egoX - 15,
      y1: egoY - 28,
      x2: egoX + 15,
      y2: egoY + 28
    };

    // Stationary Red Accident Truck on Lane 2 — strictly locked 1:1 to worldScrollPx!
    let accBox = null;
    let accXOut = null;
    let accYOut = null;
    if (state.accActive) {
      const accX = rl + lw * 1.5;
      const accY = state.accStartScreenY + (state.worldScrollPx - state.accSpawnScrollPx);
      accXOut = accX;
      accYOut = accY;

      if (accY >= -80 && accY <= h + 80) {
        const flareBlink = Math.floor(animSec * 4.5) % 2 === 0;
        [44, 68].forEach((dy, idx) => {
          ctx.fillStyle = flareBlink || idx === 0 ? "#fb923c" : "#ef4444";
          ctx.beginPath();
          ctx.arc(accX + (idx === 0 ? -8 : 8), accY + dy, 3.5, 0, Math.PI * 2);
          ctx.fill();
        });

        const pulse = (Math.sin(animSec * 4.5) + 1) / 2;
        ctx.strokeStyle = `rgba(255, 107, 44, ${0.32 + pulse * 0.48})`;
        ctx.lineWidth = 2.2;
        ctx.beginPath();
        ctx.arc(accX, accY, 26 + pulse * 14, 0, Math.PI * 2);
        ctx.stroke();

        ctx.save();
        ctx.translate(accX, accY);
        ctx.rotate(-0.32);
        ctx.fillStyle = "#dc2626";
        ctx.beginPath();
        ctx.roundRect(-18, -30, 36, 60, 6);
        ctx.fill();
        if (flareBlink) {
          ctx.fillStyle = "#fbbf24";
          ctx.beginPath();
          ctx.arc(-15, -26, 3.5, 0, Math.PI * 2);
          ctx.arc(15, 26, 3.5, 0, Math.PI * 2);
          ctx.fill();
        }
        ctx.fillStyle = "#fca5a5";
        ctx.font = '800 10px "Asta Sans", sans-serif';
        ctx.textAlign = "center";
        ctx.fillText("사고", 0, 4);
        ctx.restore();

        accBox = {
          id: "accident_truck",
          lane: 2.0,
          cx: accX,
          cy: accY,
          x1: accX - 27,
          y1: accY - 34,
          x2: accX + 27,
          y2: accY + 34
        };
      }
    }

    // Rear Alert (2.3km behind): Lane-change guidance arrow + top HUD distance sign
    if (state.scene === "rear_alert") {
      const sx = rl + lw * 1.5;
      const ex = rl + lw * 2.5;
      ctx.strokeStyle = "#ff6b2c";
      ctx.lineWidth = 3.5;
      ctx.setLineDash([8, 6]);
      ctx.lineDashOffset = -(state.worldScrollPx * 0.35) % 14;
      ctx.beginPath();
      ctx.moveTo(sx, h * 0.60);
      ctx.bezierCurveTo(sx, h * 0.48, ex, h * 0.47, ex, h * 0.34);
      ctx.stroke();
      ctx.setLineDash([]);

      const badgeW = Math.min(lw - 8, 104);
      const badgeX = sx - badgeW / 2;
      const badgeY = 122;
      ctx.fillStyle = "rgba(127, 29, 29, 0.94)";
      ctx.strokeStyle = "rgba(248, 113, 113, 0.88)";
      ctx.lineWidth = 1.5;
      ctx.beginPath();
      ctx.roundRect(badgeX, badgeY, badgeW, 40, 9);
      ctx.fill();
      ctx.stroke();
      ctx.fillStyle = "#fecaca";
      ctx.font = '800 11.5px "Asta Sans", sans-serif';
      ctx.textAlign = "center";
      ctx.fillText(`▲ ${state.rearAlertDistKm.toFixed(1)}km 전방`, sx, badgeY + 17);
      ctx.fillStyle = "#fca5a5";
      ctx.font = '700 10.5px "Asta Sans", sans-serif';
      ctx.fillText("2차로 통제", sx, badgeY + 32);
    }

    if (state.scene === "arrival_summary") {
      ctx.strokeStyle = "rgba(0, 130, 255, 0.44)";
      ctx.lineWidth = 2;
      ctx.strokeRect(egoX - 24, egoY - 40, 48, 80);
    }

    // Integrate & collision-guard Ambient Traffic Vehicles
    const placedBoxes = [egoBox];
    if (accBox) placedBoxes.push(accBox);

    traffic.forEach((car) => {
      if (state.gear === "D") {
        if (car.mode === "lead") {
          const desiredY = Math.max(170, egoY - 385);
          car.yPx += (desiredY - car.yPx) * Math.min(1, dt * 1.5);
        } else if (car.mode === "loop") {
          const relMps = (state.speedKmh - car.speedKmh) / 3.6;
          car.yPx += relMps * PX_PER_METER * dt;
          if (car.yPx > LOOP_MAX_Y) car.yPx -= LOOP_SPAN_Y;
          if (car.yPx < LOOP_MIN_Y) car.yPx += LOOP_SPAN_Y;
        }
      }

      const cx = rl + lw * (car.lane - 0.5);
      let cy = car.yPx;
      let candidate = {
        id: car.id,
        lane: car.lane,
        cx: cx,
        cy: cy,
        x1: cx - 13,
        y1: cy - 24,
        x2: cx + 13,
        y2: cy + 24
      };

      // Hard safety guard: >= 74px vertical buffer from any laterally proximate vehicle
      for (let i = 0; i < placedBoxes.length; i++) {
        const obs = placedBoxes[i];
        if (Math.abs(candidate.cx - obs.cx) < 52 && aabbIntersects(candidate, obs, 45)) {
          cy = cy <= obs.cy ? obs.y1 - 74 : obs.y2 + 74;
          car.yPx = cy;
          candidate = {
            id: car.id,
            lane: car.lane,
            cx: cx,
            cy: cy,
            x1: cx - 13,
            y1: cy - 24,
            x2: cx + 13,
            y2: cy + 24
          };
        }
      }

      if (cy >= -60 && cy <= h + 60) {
        placedBoxes.push(candidate);
        drawCarTopView(cx, cy, 26, 48, car.color, false, false);
      }
    });

    drawCarTopView(egoX, egoY, 30, 56, "#e2e8f0", true, blinkOn);

    const diag = {
      t: animSec,
      stage: state.scene,
      canvasW: w,
      canvasH: h,
      egoSpeedKmh: state.speedKmh,
      egoY: egoY,
      worldScrollPx: state.worldScrollPx,
      roadOffset: state.roadOffset,
      accActive: state.accActive,
      accVisible: accBox !== null,
      accX: accXOut,
      accY: accYOut,
      boxes: placedBoxes
    };
    window.__lastFrameDiagnostics = diag;
    return diag;
  }

  let lastTs = performance.now();
  function tick(ts) {
    if (state.deterministicVideoMode) return;
    const dt = Math.min(0.05, Math.max(0, (ts - lastTs) / 1000));
    lastTs = ts;
    state.simTimeSec += dt;
    stepSimulationAndDraw(dt, state.simTimeSec);
    requestAnimationFrame(tick);
  }

  // Deterministic Frame-Stepping API for 1920x1080@30fps Video Capture & Physics Verification
  let detLastT = null;
  let touchRipple = null;

  window.__enterDeterministicVideoMode = function () {
    clearAutoTimers();
    state.deterministicVideoMode = true;
    state.isLandscape20 = false;
    state.isLightTheme = false;
    el.iviMain.classList.remove("landscape-20");
    el.ondoWindow.classList.remove("light-theme");
    document.body.classList.add("clean-capture");
    el.clockText.textContent = "22:15";

    if (!touchRipple) {
      touchRipple = document.createElement("div");
      touchRipple.id = "demoTouchRipple";
      touchRipple.style.cssText = `
        position: fixed; width: 84px; height: 84px; border-radius: 50%;
        border: 3.5px solid #ffffff; background: rgba(255, 107, 44, 0.42);
        pointer-events: none; z-index: 9999; display: none;
        transform: translate(-50%, -50%) scale(1);
        box-shadow: 0 0 36px rgba(255, 107, 44, 0.9);
      `;
      document.body.appendChild(touchRipple);
    }

    resizeCanvas();
    state.worldScrollPx = 0.0;
    state.roadOffset = 0.0;
    detLastT = null;
    applyScene("driving");
    return true;
  };

  window.__renderFrameAt = function (t) {
    const dt = detLastT === null ? 0.0 : Math.max(0.0, Math.min(0.1, t - detLastT));
    detLastT = t;
    state.simTimeSec = t;

    if (t < 6.0) {
      if (state.scene !== "driving") applyScene("driving");
      if (t >= 5.1 && touchRipple) {
        const btn = document.getElementById("oneTouchBtn");
        if (btn) {
          const r = btn.getBoundingClientRect();
          const prog = (t - 5.1) / 0.9;
          touchRipple.style.display = "block";
          touchRipple.style.left = `${r.left + r.width * 0.5}px`;
          touchRipple.style.top = `${r.top + r.height * 0.5}px`;
          touchRipple.style.transform = `translate(-50%, -50%) scale(${0.6 + prog * 0.75})`;
          touchRipple.style.opacity = String(1 - prog * 0.45);
        }
      } else if (touchRipple) {
        touchRipple.style.display = "none";
      }
    } else if (t < 15.0) {
      if (touchRipple) touchRipple.style.display = "none";
      if (state.scene !== "stt_snapshot") applyScene("stt_snapshot");

      const fullStr = "2차로 화물차 전복, 파편 주의";
      const subT = Math.max(0, t - 6.35);
      const chars = Math.min(fullStr.length, Math.floor(subT * 9.5));
      const trNode = document.getElementById("detTranscript");
      if (trNode) trNode.textContent = `"${fullStr.slice(0, chars)}"`;

      const chipsNode = document.getElementById("detChips");
      if (chipsNode) chipsNode.style.opacity = t >= 8.3 ? "1" : "0.22";

      const bars = document.querySelectorAll("#detWave .stt-wave-bar");
      bars.forEach((b, idx) => {
        b.style.animation = "none";
        const hVal = 7 + Math.abs(Math.sin(t * 9.2 + idx * 0.95)) * 19;
        b.style.height = `${hVal.toFixed(1)}px`;
      });
    } else if (t < 23.0) {
      if (state.scene !== "cross_verified") applyScene("cross_verified");
    } else if (t < 35.0) {
      if (state.scene !== "rear_alert") {
        applyScene("rear_alert");
        state.targetEgoLane = 2.0;
      }
      state.targetEgoLane = t >= 24.6 ? 3.0 : 2.0;
    } else {
      if (state.scene !== "arrival_summary") applyScene("arrival_summary");
    }

    return stepSimulationAndDraw(dt, t);
  };

  function toggleWindowMode() {
    state.isLandscape20 = !state.isLandscape20;
    el.iviMain.classList.toggle("landscape-20", state.isLandscape20);
    el.windowModeBtn.textContent = state.isLandscape20 ? "2:0 Landscape" : "1:1 Portrait";
    if (!state.isLandscape20) {
      setTimeout(resizeCanvas, 30);
    }
  }

  function toggleTheme() {
    state.isLightTheme = !state.isLightTheme;
    el.ondoWindow.classList.toggle("light-theme", state.isLightTheme);
    el.themeToggleBtn.textContent = state.isLightTheme ? "주간" : "야간";
  }

  function runAutoDirector() {
    clearAutoTimers();
    applyScene("driving");
    state.autoTimers.push(setTimeout(() => applyScene("stt_snapshot"), 4000));
    state.autoTimers.push(setTimeout(() => applyScene("cross_verified"), 9500));
    state.autoTimers.push(setTimeout(() => applyScene("rear_alert"), 15000));
    state.autoTimers.push(setTimeout(() => applyScene("arrival_summary"), 23000));
  }

  function updateClock() {
    if (state.deterministicVideoMode) return;
    const now = new Date();
    const hh = String(now.getHours()).padStart(2, "0");
    const mm = String(now.getMinutes()).padStart(2, "0");
    el.clockText.textContent = `${hh}:${mm}`;
  }

  // Interactive Event Listeners
  el.hazardBtn.addEventListener("click", () => setHazard(!state.hazardOn));
  el.windowModeBtn.addEventListener("click", toggleWindowMode);
  el.windowDivider.addEventListener("click", toggleWindowMode);
  el.themeToggleBtn.addEventListener("click", toggleTheme);

  el.sceneBtns.forEach((btn) => {
    btn.addEventListener("click", () => {
      clearAutoTimers();
      applyScene(btn.getAttribute("data-scene-btn"));
    });
  });

  el.autoPlayBtn.addEventListener("click", runAutoDirector);

  el.cleanToggleBtn.addEventListener("click", () => {
    document.body.classList.toggle("clean-capture");
    setTimeout(resizeCanvas, 50);
  });

  el.muteBtn.addEventListener("click", () => {
    state.ttsEnabled = !state.ttsEnabled;
    el.muteBtn.textContent = state.ttsEnabled ? "음성 안내 ON" : "음성 안내 OFF";
    if (!state.ttsEnabled && "speechSynthesis" in window) {
      window.speechSynthesis.cancel();
    }
  });

  window.addEventListener("keydown", (e) => {
    if (e.key === "1") { clearAutoTimers(); applyScene("driving"); }
    if (e.key === "2" || e.code === "Space") {
      e.preventDefault();
      clearAutoTimers();
      applyScene("stt_snapshot");
    }
    if (e.key === "3") { clearAutoTimers(); applyScene("cross_verified"); }
    if (e.key === "4") { clearAutoTimers(); applyScene("rear_alert"); }
    if (e.key === "5") { clearAutoTimers(); applyScene("arrival_summary"); }
    if (e.key === "a" || e.key === "A") runAutoDirector();
    if (e.key === "w" || e.key === "W") toggleWindowMode();
    if (e.key === "t" || e.key === "T") toggleTheme();
    if (e.key === "h" || e.key === "H") {
      document.body.classList.toggle("clean-capture");
      setTimeout(resizeCanvas, 50);
    }
  });

  window.addEventListener("resize", resizeCanvas);

  // Boot
  updateClock();
  setInterval(updateClock, 15000);
  resizeCanvas();
  applyScene("driving");
  requestAnimationFrame(tick);
})();
