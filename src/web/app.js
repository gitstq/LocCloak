// locctl Web UI 前端交互逻辑
(function () {
  'use strict';

  // 修复 Leaflet 默认 icon 路径解析失败问题
  if (typeof L !== 'undefined' && L.Icon && L.Icon.Default) {
    delete L.Icon.Default.prototype._getIconUrl;
    L.Icon.Default.mergeOptions({
      iconRetinaUrl: 'vendor/leaflet/images/marker-icon-2x.png',
      iconUrl: 'vendor/leaflet/images/marker-icon.png',
      shadowUrl: 'vendor/leaflet/images/marker-shadow.png',
      imagePath: 'vendor/leaflet/images/',
    });
  }

  // 高清矢量定位图钉（杜绝任何图片资源丢失问题，且自带精美发光阴影与精确定位针尖）
  const locationPinIcon = typeof L !== 'undefined' ? L.divIcon({
    className: 'custom-map-pin',
    html: `
      <div class="pin-marker-body">
        <svg viewBox="0 0 32 44" width="32" height="44" style="filter: drop-shadow(0 4px 8px rgba(0,0,0,0.5));">
          <path d="M16 0C7.16 0 0 7.16 0 16c0 11.2 14.1 26.3 14.7 27a1.8 1.8 0 0 0 2.6 0C17.9 42.3 32 27.2 32 16 32 7.16 24.84 0 16 0z" fill="#ff3838"/>
          <circle cx="16" cy="15" r="6.5" fill="#ffffff"/>
          <circle cx="16" cy="15" r="3" fill="#ff3838"/>
        </svg>
        <div class="pin-pulse-wave"></div>
      </div>
    `,
    iconSize: [32, 44],
    iconAnchor: [16, 43],
  }) : null;

  // ----------------------------------------------------------- 核心状态
  let map = null;
  let currentMarker = null;
  let wanderCircle = null;
  let routePolyline = null;
  let routeProgressMarker = null;
  let baseLayers = {};
  let currentLayerKey = 'amap-vec';

  let presetsList = [];
  let currentTaskState = { mode: 'idle' };
  let activeTabName = 'tab-hold';
  let activeRouteData = null;

  // DOM 元素引用
  const dom = {
    deviceSelect: document.getElementById('deviceSelect'),
    btnRefreshDevices: document.getElementById('btnRefreshDevices'),
    adbBadge: document.getElementById('adbBadge'),
    adbText: document.getElementById('adbText'),
    permChip: document.getElementById('permChip'),
    permVal: document.getElementById('permVal'),
    locationSwitch: document.getElementById('locationSwitch'),
    pillGps: document.getElementById('pillGps'),
    pillNetwork: document.getElementById('pillNetwork'),
    pillFused: document.getElementById('pillFused'),
    btnGlobalStop: document.getElementById('btnGlobalStop'),

    // 坐标输入
    inputLat: document.getElementById('inputLat'),
    inputLng: document.getElementById('inputLng'),
    selectCs: document.getElementById('selectCs'),
    inputTargetName: document.getElementById('inputTargetName'),
    txtWgsPreview: document.getElementById('txtWgsPreview'),
    txtGcjPreview: document.getElementById('txtGcjPreview'),

    // 任务横幅
    taskBanner: document.getElementById('taskBanner'),
    taskBannerMode: document.getElementById('taskBannerMode'),
    taskBannerDetail: document.getElementById('taskBannerDetail'),
    btnStopCurrentTask: document.getElementById('btnStopCurrentTask'),

    // Hold 模式
    holdInterval: document.getElementById('holdInterval'),
    holdAccuracy: document.getElementById('holdAccuracy'),
    btnStartHold: document.getElementById('btnStartHold'),
    btnGotoOnce: document.getElementById('btnGotoOnce'),
    presetCatFilter: document.getElementById('presetCatFilter'),
    presetSearchInput: document.getElementById('presetSearchInput'),
    presetChips: document.getElementById('presetChips'),

    // Wander 漫游
    wanderRadius: document.getElementById('wanderRadius'),
    valWanderRadius: document.getElementById('valWanderRadius'),
    wanderInterval: document.getElementById('wanderInterval'),
    valWanderInterval: document.getElementById('valWanderInterval'),
    wanderAccuracy: document.getElementById('wanderAccuracy'),
    btnStartWander: document.getElementById('btnStartWander'),

    // Path 轨迹
    routeSelect: document.getElementById('routeSelect'),
    gpxDropzone: document.getElementById('gpxDropzone'),
    gpxFileInput: document.getElementById('gpxFileInput'),
    gpxDropText: document.getElementById('gpxDropText'),
    routeSummary: document.getElementById('routeSummary'),
    txtRoutePoints: document.getElementById('txtRoutePoints'),
    txtRouteDistance: document.getElementById('txtRouteDistance'),
    txtRouteEstTime: document.getElementById('txtRouteEstTime'),
    routeSpeed: document.getElementById('routeSpeed'),
    valRouteSpeed: document.getElementById('valRouteSpeed'),
    btnStartPath: document.getElementById('btnStartPath'),

    // 自检与工具
    btnRunDoctor: document.getElementById('btnRunDoctor'),
    btnFixDoctor: document.getElementById('btnFixDoctor'),
    docDevice: document.getElementById('docDevice'),
    docAndroid: document.getElementById('docAndroid'),
    docRoot: document.getElementById('docRoot'),
    docPerm: document.getElementById('docPerm'),
    docSwitch: document.getElementById('docSwitch'),

    calcLat: document.getElementById('calcLat'),
    calcLng: document.getElementById('calcLng'),
    calcFromCs: document.getElementById('calcFromCs'),
    btnRunConvert: document.getElementById('btnRunConvert'),
    convWgs: document.getElementById('convWgs'),
    convGcj: document.getElementById('convGcj'),
    convBd: document.getElementById('convBd'),
    convShift: document.getElementById('convShift'),

    // 抽屉与终端
    consoleDrawer: document.getElementById('consoleDrawer'),
    drawerHeader: document.getElementById('drawerHeader'),
    consoleOutput: document.getElementById('consoleOutput'),
    chkAutoScroll: document.getElementById('chkAutoScroll'),
    btnClearLogs: document.getElementById('btnClearLogs'),
    btnToggleDrawer: document.getElementById('btnToggleDrawer'),
    btnResetView: document.getElementById('btnResetView'),
    btnCenterChina: document.getElementById('btnCenterChina'),
  };

  // ----------------------------------------------------------- 初始化与启动
  async function init() {
    initMap();
    bindEvents();
    initSseLogs();
    await refreshStatus();
    await loadPresets();
    await loadRoutes();
    updateCoordPreviews();

    // 定期轮询全局状态
    setInterval(refreshStatus, 3500);
  }

  // ----------------------------------------------------------- 地图初始化
  function initMap() {
    const defaultCenter = [30.244236, 120.141566]; // 西湖
    map = L.map('map', {
      zoomControl: true,
      attributionControl: false,
    }).setView(defaultCenter, 14);

    // 瓦片图层定义
    baseLayers['amap-vec'] = L.tileLayer(
      'https://webrd0{s}.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&x={x}&y={y}&z={z}',
      { subdomains: ['1', '2', '3', '4'], maxZoom: 18 }
    );
    baseLayers['amap-img'] = L.tileLayer(
      'https://webst0{s}.is.autonavi.com/appmaptile?style=6&x={x}&y={y}&z={z}',
      { subdomains: ['1', '2', '3', '4'], maxZoom: 18 }
    );
    baseLayers['osm'] = L.tileLayer(
      'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',
      { subdomains: ['a', 'b', 'c'], maxZoom: 19 }
    );

    baseLayers['amap-vec'].addTo(map);

    // 初始标记点（可拖动）
    currentMarker = L.marker(defaultCenter, {
      draggable: true,
      icon: locationPinIcon || new L.Icon.Default(),
      title: '拖动可调整注入位置',
    }).addTo(map);

    currentMarker.on('dragend', function (e) {
      const pos = e.target.getLatLng();
      dom.inputLat.value = pos.lat.toFixed(6);
      dom.inputLng.value = pos.lng.toFixed(6);
      dom.selectCs.value = 'gcj02'; // 高德底图上拖拽拾取的是 GCJ-02 坐标
      dom.inputTargetName.value = '地图自定义点';
      updateCoordPreviews();
      updateWanderCircle();
    });

    // 地图点击拾取坐标
    map.on('click', function (e) {
      const { lat, lng } = e.latlng;
      setPoint(lat, lng, 'gcj02', '地图点击位置');
    });

    // 图层切换
    document.querySelectorAll('.layer-btn').forEach((btn) => {
      btn.addEventListener('click', () => {
        document.querySelectorAll('.layer-btn').forEach((b) => b.classList.remove('active'));
        btn.classList.add('active');
        const key = btn.dataset.layer;
        if (baseLayers[key] && key !== currentLayerKey) {
          map.removeLayer(baseLayers[currentLayerKey]);
          baseLayers[key].addTo(map);
          currentLayerKey = key;
        }
      });
    });

    // 快捷视角
    dom.btnResetView.addEventListener('click', () => {
      if (currentMarker) {
        map.setView(currentMarker.getLatLng(), 15, { animate: true });
      }
    });

    dom.btnCenterChina.addEventListener('click', () => {
      map.setView([35.86166, 104.195397], 4, { animate: true });
    });
  }

  function setPoint(lat, lng, cs = 'gcj02', name = '') {
    dom.inputLat.value = Number(lat).toFixed(6);
    dom.inputLng.value = Number(lng).toFixed(6);
    dom.selectCs.value = cs;
    if (name) dom.inputTargetName.value = name;

    const latlng = [Number(lat), Number(lng)];
    if (currentMarker) {
      currentMarker.setLatLng(latlng);
    } else {
      currentMarker = L.marker(latlng, {
        draggable: true,
        icon: locationPinIcon || new L.Icon.Default(),
      }).addTo(map);
    }
    map.panTo(latlng);
    updateCoordPreviews();
    updateWanderCircle();
  }

  function updateWanderCircle() {
    const lat = Number(dom.inputLat.value);
    const lng = Number(dom.inputLng.value);
    const radius = Number(dom.wanderRadius.value);

    if (activeTabName === 'tab-wander' || currentTaskState.mode === 'wander') {
      if (!wanderCircle) {
        wanderCircle = L.circle([lat, lng], {
          radius: radius,
          color: '#00e676',
          fillColor: '#00e676',
          fillOpacity: 0.15,
          weight: 2,
          dashArray: '4, 4',
        }).addTo(map);
      } else {
        wanderCircle.setLatLng([lat, lng]);
        wanderCircle.setRadius(radius);
      }
    } else {
      if (wanderCircle) {
        map.removeLayer(wanderCircle);
        wanderCircle = null;
      }
    }
  }

  // ----------------------------------------------------------- 坐标预览计算
  async function updateCoordPreviews() {
    const lat = Number(dom.inputLat.value);
    const lng = Number(dom.inputLng.value);
    const fromCs = dom.selectCs.value;

    if (!Number.isFinite(lat) || !Number.isFinite(lng)) return;

    try {
      const res = await apiPost('/api/convert', { lat, lng, from: fromCs });
      if (res.ok) {
        dom.txtWgsPreview.textContent = `${res.wgs84.lat.toFixed(6)}, ${res.wgs84.lng.toFixed(6)}`;
        dom.txtGcjPreview.textContent = `${res.gcj02.lat.toFixed(6)}, ${res.gcj02.lng.toFixed(6)}`;
      }
    } catch {}
  }

  // ----------------------------------------------------------- API 通讯与状态
  async function apiGet(path) {
    const res = await fetch(path);
    return res.json();
  }

  async function apiPost(path, data = {}) {
    const res = await fetch(path, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data),
    });
    return res.json();
  }

  async function refreshStatus() {
    try {
      const data = await apiGet('/api/status');
      if (!data.ok) {
        dom.adbBadge.textContent = 'ADB 离线';
        dom.adbBadge.className = 'badge';
        return;
      }

      // ADB 状态显示
      if (data.adb.builtin) {
        dom.adbText.textContent = '内置 ADB (开箱即用)';
        dom.adbBadge.className = 'badge badge-adb builtin';
      } else {
        dom.adbText.textContent = '系统 ADB';
        dom.adbBadge.className = 'badge badge-adb';
      }

      // 设备列表填充
      const currentSerial = data.serial || '';
      dom.deviceSelect.innerHTML = '';
      if (!data.devices || data.devices.length === 0) {
        dom.deviceSelect.innerHTML = '<option value="">未检测到 Android 设备 (请插上 USB)</option>';
        document.getElementById('deviceDot').className = 'status-dot';
      } else {
        document.getElementById('deviceDot').className = 'status-dot dot-online';
        data.devices.forEach((d) => {
          const opt = document.createElement('option');
          opt.value = d.id;
          opt.textContent = `${d.model || d.id} (${d.state})`;
          if (d.id === currentSerial) opt.selected = true;
          dom.deviceSelect.appendChild(opt);
        });
      }

      // Mock 权限状态
      if (data.permissionGranted) {
        dom.permVal.textContent = '已允许';
        dom.permChip.className = 'status-chip granted';
      } else {
        dom.permVal.textContent = '未授予 (点击体检)';
        dom.permChip.className = 'status-chip denied';
      }

      // 系统定位总开关
      if (data.mockStatus && data.mockStatus.locationEnabled != null) {
        dom.locationSwitch.checked = Boolean(data.mockStatus.locationEnabled);
      }

      // Mock Provider 状态
      if (data.mockStatus && data.mockStatus.mocked) {
        toggleMockPill(dom.pillGps, data.mockStatus.mocked.gps);
        toggleMockPill(dom.pillNetwork, data.mockStatus.mocked.network);
        toggleMockPill(dom.pillFused, data.mockStatus.mocked.fused);
      }

      // 任务状态同步
      if (data.task) {
        updateTaskUi(data.task);
      }
    } catch (err) {
      console.warn('refreshStatus error:', err);
    }
  }

  function toggleMockPill(el, mocked) {
    if (mocked) {
      el.classList.add('mocked');
    } else {
      el.classList.remove('mocked');
    }
  }

  // ----------------------------------------------------------- 任务状态渲染
  function updateTaskUi(task) {
    currentTaskState = task;
    if (task.mode === 'idle') {
      dom.taskBanner.classList.add('hidden');
      if (routeProgressMarker) {
        map.removeLayer(routeProgressMarker);
        routeProgressMarker = null;
      }
    } else {
      dom.taskBanner.classList.remove('hidden');
      const modeNames = {
        hold: '常驻推送模式',
        wander: '随机漫游模式',
        path: '轨迹回放模式',
      };
      dom.taskBannerMode.textContent = `${modeNames[task.mode] || task.mode} 运行中`;
      let detail = `已推送 ${task.pushes} 次 · 运行时长 ${task.durationSeconds || 0} 秒`;

      if (task.mode === 'path' && task.pathProgress) {
        const p = task.pathProgress;
        const pct = Math.round((p.currentIdx / (p.totalPoints - 1 || 1)) * 100);
        detail = `进度 ${pct}% (${p.travelledMeters}m / ${p.totalMeters}m) · ${p.speedKmh} km/h`;
      }
      dom.taskBannerDetail.textContent = detail;

      // 如果有当前移动点位，更新地图小光标
      if (task.currentPoint) {
        const gcj = task.currentPoint.gcj02 || task.currentPoint.wgs84;
        const pt = [gcj.lat, gcj.lng];
        if (task.mode === 'wander') {
          if (currentMarker) currentMarker.setLatLng(pt);
        } else if (task.mode === 'path') {
          if (!routeProgressMarker) {
            routeProgressMarker = L.circleMarker(pt, {
              radius: 7,
              color: '#00e5ff',
              fillColor: '#fff',
              fillOpacity: 1,
              weight: 3,
            }).addTo(map);
          } else {
            routeProgressMarker.setLatLng(pt);
          }
        }
      }
    }
  }

  // ----------------------------------------------------------- 预设地点与路线
  async function loadPresets() {
    try {
      const res = await apiGet('/api/presets');
      if (!res.ok) return;
      presetsList = res.presets || [];

      // 分类下拉
      dom.presetCatFilter.innerHTML = '<option value="all">全部分类</option>';
      (res.categories || []).forEach((cat) => {
        const opt = document.createElement('option');
        opt.value = cat;
        opt.textContent = cat;
        dom.presetCatFilter.appendChild(opt);
      });

      renderPresets(presetsList);
    } catch (err) {
      console.warn('loadPresets error:', err);
    }
  }

  function renderPresets(list) {
    dom.presetChips.innerHTML = '';
    const slice = list.slice(0, 48);
    slice.forEach((p) => {
      const chip = document.createElement('span');
      chip.className = 'preset-chip';
      chip.textContent = `${p.name} (${p.cat})`;
      chip.addEventListener('click', () => {
        setPoint(p.lat, p.lng, p.cs, p.name);
      });
      dom.presetChips.appendChild(chip);
    });
  }

  function filterPresets() {
    const kw = dom.presetSearchInput.value.trim().toLowerCase();
    const cat = dom.presetCatFilter.value;
    const filtered = presetsList.filter((p) => {
      const matchCat = cat === 'all' || p.cat === cat;
      const matchKw = !kw || p.name.toLowerCase().includes(kw) || p.cat.toLowerCase().includes(kw);
      return matchCat && matchKw;
    });
    renderPresets(filtered);
  }

  async function loadRoutes() {
    try {
      const res = await apiGet('/api/routes');
      if (!res.ok || !res.routes) return;
      dom.routeSelect.innerHTML = '<option value="">-- 请选择路线 --</option>';
      res.routes.forEach((r) => {
        const opt = document.createElement('option');
        opt.value = r.filename;
        opt.textContent = `${r.filename} (${(r.totalMeters / 1000).toFixed(1)} km, ${r.pointsCount} 点)`;
        dom.routeSelect.appendChild(opt);
      });
    } catch {}
  }

  // ----------------------------------------------------------- 实时日志 SSE
  function initSseLogs() {
    try {
      const eventSource = new EventSource('/api/logs/stream');
      eventSource.onmessage = function (e) {
        try {
          const packet = JSON.parse(e.data);
          if (packet.type === 'history') {
            dom.consoleOutput.innerHTML = '';
            packet.data.forEach(renderLogItem);
          } else if (packet.type === 'log') {
            renderLogItem(packet.data);
          } else if (packet.type === 'task') {
            updateTaskUi(packet.data);
          }
        } catch {}
      };
      eventSource.onerror = function () {
        console.warn('SSE 连接断开，稍后重连...');
      };
    } catch {}
  }

  function renderLogItem(log) {
    const row = document.createElement('div');
    row.className = `log-row log-${log.level}`;
    row.innerHTML = `<span class="log-time">[${log.time}]</span><span class="log-level">${log.level.toUpperCase()}</span><span class="log-msg">${escapeHtml(log.message)}</span>`;
    dom.consoleOutput.appendChild(row);

    if (dom.chkAutoScroll.checked) {
      dom.consoleOutput.scrollTop = dom.consoleOutput.scrollHeight;
    }
  }

  function escapeHtml(str) {
    return String(str || '')
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;');
  }

  // ----------------------------------------------------------- 事件绑定
  function bindEvents() {
    // Tab 导航切换
    document.querySelectorAll('.tab-btn').forEach((btn) => {
      btn.addEventListener('click', () => {
        document.querySelectorAll('.tab-btn').forEach((b) => b.classList.remove('active'));
        document.querySelectorAll('.tab-pane').forEach((p) => p.classList.remove('active'));
        btn.classList.add('active');
        activeTabName = btn.dataset.tab;
        const targetPane = document.getElementById(activeTabName);
        if (targetPane) targetPane.classList.add('active');
        updateWanderCircle();
      });
    });

    // 坐标输入联动
    dom.inputLat.addEventListener('change', () => {
      setPoint(dom.inputLat.value, dom.inputLng.value, dom.selectCs.value, dom.inputTargetName.value);
    });
    dom.inputLng.addEventListener('change', () => {
      setPoint(dom.inputLat.value, dom.inputLng.value, dom.selectCs.value, dom.inputTargetName.value);
    });
    dom.selectCs.addEventListener('change', updateCoordPreviews);

    // 搜索预设地点
    dom.presetSearchInput.addEventListener('input', filterPresets);
    dom.presetCatFilter.addEventListener('change', filterPresets);

    // 设备选择与刷新
    dom.deviceSelect.addEventListener('change', async () => {
      const serial = dom.deviceSelect.value;
      await apiPost('/api/device/select', { serial });
      refreshStatus();
    });
    dom.btnRefreshDevices.addEventListener('click', refreshStatus);

    // 系统定位开关
    dom.locationSwitch.addEventListener('change', async () => {
      const enabled = dom.locationSwitch.checked;
      await apiPost('/api/location/enable', { enabled });
    });

    // 停止/还原
    dom.btnGlobalStop.addEventListener('click', async () => {
      if (confirm('确认停止所有注入并还原为真实定位？')) {
        await apiPost('/api/inject/stop');
        refreshStatus();
      }
    });
    dom.btnStopCurrentTask.addEventListener('click', async () => {
      await apiPost('/api/inject/stop');
      refreshStatus();
    });

    // 开始常驻推送 (Hold)
    dom.btnStartHold.addEventListener('click', async () => {
      const lat = Number(dom.inputLat.value);
      const lng = Number(dom.inputLng.value);
      const cs = dom.selectCs.value;
      const interval = Number(dom.holdInterval.value);
      const accuracy = Number(dom.holdAccuracy.value);
      const name = dom.inputTargetName.value;

      const checkedProviders = Array.from(document.querySelectorAll('input[name="provider"]:checked')).map(
        (el) => el.value
      );

      const res = await apiPost('/api/inject/hold', {
        lat,
        lng,
        cs,
        interval,
        accuracy,
        name,
        providers: checkedProviders,
      });

      if (!res.ok) alert('启动常驻推送失败: ' + res.error);
      refreshStatus();
    });

    // 单次瞬移注入
    dom.btnGotoOnce.addEventListener('click', async () => {
      const lat = Number(dom.inputLat.value);
      const lng = Number(dom.inputLng.value);
      const cs = dom.selectCs.value;
      const accuracy = Number(dom.holdAccuracy.value);
      const name = dom.inputTargetName.value;
      const checkedProviders = Array.from(document.querySelectorAll('input[name="provider"]:checked')).map(
        (el) => el.value
      );

      const res = await apiPost('/api/inject/goto', {
        lat,
        lng,
        cs,
        accuracy,
        name,
        providers: checkedProviders,
      });

      if (!res.ok) alert('单次瞬移注入失败: ' + res.error);
      refreshStatus();
    });

    // 漫游滑块联动
    dom.wanderRadius.addEventListener('input', () => {
      dom.valWanderRadius.textContent = `${dom.wanderRadius.value} m`;
      updateWanderCircle();
    });
    dom.wanderInterval.addEventListener('input', () => {
      dom.valWanderInterval.textContent = `${dom.wanderInterval.value} s`;
    });

    // 开始漫游
    dom.btnStartWander.addEventListener('click', async () => {
      const lat = Number(dom.inputLat.value);
      const lng = Number(dom.inputLng.value);
      const cs = dom.selectCs.value;
      const radius = Number(dom.wanderRadius.value);
      const interval = Number(dom.wanderInterval.value) * 1000;
      const accuracy = Number(dom.wanderAccuracy.value);
      const name = dom.inputTargetName.value;

      const res = await apiPost('/api/inject/wander', {
        lat,
        lng,
        cs,
        radius,
        interval,
        accuracy,
        name,
      });

      if (!res.ok) alert('启动随机漫游失败: ' + res.error);
      refreshStatus();
    });

    // 轨迹速度联动
    dom.routeSpeed.addEventListener('input', () => {
      dom.valRouteSpeed.textContent = `${dom.routeSpeed.value} km/h`;
      recalcRouteEstTime();
    });
    document.querySelectorAll('.speed-presets .btn-chip').forEach((chip) => {
      chip.addEventListener('click', () => {
        dom.routeSpeed.value = chip.dataset.speed;
        dom.valRouteSpeed.textContent = `${chip.dataset.speed} km/h`;
        recalcRouteEstTime();
      });
    });

    // 轨迹选择联动
    dom.routeSelect.addEventListener('change', async () => {
      const file = dom.routeSelect.value;
      if (!file) return;
      try {
        const res = await apiGet('/api/routes');
        const item = (res.routes || []).find((r) => r.filename === file);
        if (item) {
          activeRouteData = { routeName: file, totalMeters: item.totalMeters };
          dom.routeSummary.classList.remove('hidden');
          dom.txtRoutePoints.textContent = `${item.pointsCount} 个`;
          dom.txtRouteDistance.textContent = `${(item.totalMeters / 1000).toFixed(2)} km`;
          recalcRouteEstTime();

          if (item.previewPoints && item.previewPoints.length > 0) {
            drawRouteOnMap(item.previewPoints);
          }
        }
      } catch {}
    });

    // GPX 文件拖拽与上传
    dom.gpxDropzone.addEventListener('click', () => dom.gpxFileInput.click());
    dom.gpxFileInput.addEventListener('change', handleGpxFile);
    dom.gpxDropzone.addEventListener('dragover', (e) => {
      e.preventDefault();
      dom.gpxDropzone.classList.add('dragover');
    });
    dom.gpxDropzone.addEventListener('dragleave', () => {
      dom.gpxDropzone.classList.remove('dragover');
    });
    dom.gpxDropzone.addEventListener('drop', (e) => {
      e.preventDefault();
      dom.gpxDropzone.classList.remove('dragover');
      if (e.dataTransfer.files.length) {
        handleFileRead(e.dataTransfer.files[0]);
      }
    });

    // 开始回放轨迹
    dom.btnStartPath.addEventListener('click', async () => {
      if (!activeRouteData) {
        return alert('请先选择或上传 GPX 轨迹文件！');
      }
      const speed = Number(dom.routeSpeed.value);
      const payload = {
        speed,
        routeName: activeRouteData.routeName,
        gpxContent: activeRouteData.gpxContent,
      };

      const res = await apiPost('/api/inject/path', payload);
      if (!res.ok) alert('启动轨迹回放失败: ' + res.error);
      refreshStatus();
    });

    // 自检与修复
    dom.btnRunDoctor.addEventListener('click', runDoctor);
    dom.btnFixDoctor.addEventListener('click', () => runDoctor(true));

    // 独立坐标换算
    dom.btnRunConvert.addEventListener('click', async () => {
      const lat = Number(dom.calcLat.value);
      const lng = Number(dom.calcLng.value);
      const from = dom.calcFromCs.value;
      const res = await apiPost('/api/convert', { lat, lng, from });
      if (res.ok) {
        dom.convWgs.textContent = `${res.wgs84.lat.toFixed(7)}, ${res.wgs84.lng.toFixed(7)}`;
        dom.convGcj.textContent = `${res.gcj02.lat.toFixed(7)}, ${res.gcj02.lng.toFixed(7)}`;
        dom.convBd.textContent = `${res.bd09.lat.toFixed(7)}, ${res.bd09.lng.toFixed(7)}`;
        dom.convShift.textContent = `${res.shiftMeters} 米`;
      }
    });

    // 终端清屏与抽屉收起
    dom.btnClearLogs.addEventListener('click', () => {
      dom.consoleOutput.innerHTML = '';
    });
    dom.btnToggleDrawer.addEventListener('click', () => {
      dom.consoleDrawer.classList.toggle('collapsed');
      dom.btnToggleDrawer.textContent = dom.consoleDrawer.classList.contains('collapsed') ? '展开' : '折叠';
    });
  }

  function handleGpxFile(e) {
    if (e.target.files.length) {
      handleFileRead(e.target.files[0]);
    }
  }

  function handleFileRead(file) {
    dom.gpxDropText.textContent = `已选择: ${file.name}`;
    const reader = new FileReader();
    reader.onload = async function (evt) {
      const content = evt.target.result;
      const res = await apiPost('/api/routes/parse', { content });
      if (res.ok) {
        activeRouteData = { gpxContent: content, totalMeters: res.stats.totalMeters };
        dom.routeSummary.classList.remove('hidden');
        dom.txtRoutePoints.textContent = `${res.stats.count} 个`;
        dom.txtRouteDistance.textContent = `${(res.stats.totalMeters / 1000).toFixed(2)} km`;
        recalcRouteEstTime();
        if (res.points) {
          drawRouteOnMap(res.points);
        }
      } else {
        alert('解析 GPX 失败: ' + res.error);
      }
    };
    reader.readAsText(file);
  }

  function drawRouteOnMap(points) {
    if (routePolyline) {
      map.removeLayer(routePolyline);
    }
    const latlngs = points.map((p) => [p.lat, p.lng]);
    routePolyline = L.polyline(latlngs, {
      color: '#00e5ff',
      weight: 4,
      opacity: 0.85,
    }).addTo(map);

    map.fitBounds(routePolyline.getBounds(), { padding: [30, 30] });
  }

  function recalcRouteEstTime() {
    if (!activeRouteData || !activeRouteData.totalMeters) return;
    const speedKmh = Number(dom.routeSpeed.value);
    const sec = Math.round((activeRouteData.totalMeters / 1000 / speedKmh) * 3600);
    const min = Math.floor(sec / 60);
    const remSec = sec % 60;
    dom.txtRouteEstTime.textContent = min > 0 ? `${min}分${remSec}秒` : `${sec}秒`;
  }

  async function runDoctor(fix = false) {
    try {
      const res = await apiPost('/api/doctor', { fix });
      if (res.ok && res.result) {
        const info = res.result.info || {};
        dom.docDevice.textContent = `${info.brand || ''} ${info.model || '未知'}`;
        dom.docAndroid.textContent = `Android ${info.release || '-'} (API ${info.sdk || '-'})`;
        dom.docRoot.textContent = info.rooted ? '已 Root' : '无 Root (ADB 模式注入)';
        dom.docPerm.textContent = res.result.granted ? '已授予' : '未授予';
        dom.docPerm.style.color = res.result.granted ? '#00e676' : '#ff5252';
        dom.docSwitch.textContent = res.result.locationEnabled ? '开启' : '关闭';
        dom.docSwitch.style.color = res.result.locationEnabled ? '#00e676' : '#ff5252';
      }
    } catch (err) {
      alert('诊断出错: ' + err.message);
    }
  }

  // 启动
  window.addEventListener('DOMContentLoaded', init);
})();
