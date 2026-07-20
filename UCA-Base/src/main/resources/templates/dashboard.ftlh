<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta name="description" content="UCA-Base 统一控制面运行与子业务系统注册状态">
    <title>${systemName} · 统一控制面</title>
    <link rel="stylesheet" href="/css/dashboard.css">
    <script defer src="/js/dashboard.js"></script>
</head>
<body data-started-at="${startedAt}" data-rendered-at="${renderedAt}">
<header class="topbar">
    <a class="brand" href="/" aria-label="返回 UCA-Base 首页">
        <span class="brand-mark" aria-hidden="true">U</span>
        <span>
            <strong>${systemName}</strong>
            <small>Unified Control Plane</small>
        </span>
    </a>
    <nav class="topbar-links" aria-label="系统接口">
        <a href="/actuator/health" target="_blank" rel="noreferrer">健康接口</a>
        <a href="/api/v1/applications" target="_blank" rel="noreferrer">注册信息 API</a>
    </nav>
</header>

<main class="page-shell">
    <section class="hero" aria-labelledby="page-title">
        <div>
            <p class="eyebrow">统一控制面 · 基础服务</p>
            <h1 id="page-title">系统运行与子业务注册总览</h1>
            <p class="hero-copy">集中查看 UCA-Base 运行状态、已接入子业务系统、在线实例及最近心跳。</p>
        </div>
        <button class="refresh-button" id="refresh-button" type="button">
            <span class="refresh-icon" aria-hidden="true">↻</span>
            <span>立即刷新</span>
        </button>
    </section>

    <div class="notice" id="notice" role="status" aria-live="polite" hidden></div>

    <section class="overview-grid" aria-label="系统概览">
        <article class="system-card">
            <div class="card-heading">
                <div>
                    <p class="section-kicker">SYSTEM</p>
                    <h2>系统信息</h2>
                </div>
                <span class="health-badge health-checking" id="health-badge">
                    <span class="health-dot" aria-hidden="true"></span>
                    <span id="health-status">检查中</span>
                </span>
            </div>
            <dl class="system-details">
                <div>
                    <dt>服务名称</dt>
                    <dd>${systemName}</dd>
                </div>
                <div>
                    <dt>应用标识</dt>
                    <dd>${applicationName}</dd>
                </div>
                <div>
                    <dt>当前版本</dt>
                    <dd>v${version}</dd>
                </div>
                <div>
                    <dt>Java 版本</dt>
                    <dd>${javaVersion}</dd>
                </div>
                <div>
                    <dt>启动时间</dt>
                    <dd id="started-at">${startedAt}</dd>
                </div>
                <div>
                    <dt>运行时长</dt>
                    <dd id="uptime">计算中</dd>
                </div>
            </dl>
        </article>

        <div class="stats-grid">
            <article class="stat-card stat-applications">
                <span class="stat-label">已注册子业务</span>
                <strong id="application-count">—</strong>
                <span class="stat-unit">个应用</span>
            </article>
            <article class="stat-card stat-instances">
                <span class="stat-label">实例总数</span>
                <strong id="instance-count">—</strong>
                <span class="stat-unit">个实例</span>
            </article>
            <article class="stat-card stat-online">
                <span class="stat-label">在线实例</span>
                <strong id="online-count">—</strong>
                <span class="stat-unit">ONLINE</span>
            </article>
            <article class="stat-card stat-offline">
                <span class="stat-label">离线实例</span>
                <strong id="offline-count">—</strong>
                <span class="stat-unit">OFFLINE</span>
            </article>
        </div>
    </section>

    <section class="registry-card" aria-labelledby="registry-title">
        <div class="registry-heading">
            <div>
                <p class="section-kicker">REGISTRY</p>
                <h2 id="registry-title">子业务系统注册信息</h2>
                <p>实例在线状态由子业务系统主动心跳判定，页面每 5 秒自动刷新。</p>
            </div>
            <div class="refresh-meta">
                <span>最近刷新</span>
                <time id="last-refreshed">尚未刷新</time>
            </div>
        </div>

        <div class="table-wrap" id="table-wrap" hidden>
            <table>
                <thead>
                <tr>
                    <th scope="col">子业务系统</th>
                    <th scope="col">实例</th>
                    <th scope="col">状态</th>
                    <th scope="col">访问地址</th>
                    <th scope="col">版本</th>
                    <th scope="col">最近心跳</th>
                    <th scope="col">元数据</th>
                </tr>
                </thead>
                <tbody id="instance-table-body"></tbody>
            </table>
        </div>

        <div class="empty-state" id="empty-state">
            <div class="empty-symbol" aria-hidden="true">⌁</div>
            <h3>暂无已注册的子业务系统</h3>
            <p>子业务系统启动并完成注册后，实例信息会自动显示在这里。</p>
        </div>

        <noscript>
            <p class="noscript-message">页面需要启用 JavaScript 才能读取实时注册信息。</p>
        </noscript>
    </section>
</main>

<footer class="footer">
    <span>${systemName} v${version}</span>
    <span>注册 · 发现 · 接入 · 状态管理</span>
</footer>
</body>
</html>
