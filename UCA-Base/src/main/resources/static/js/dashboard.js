(() => {
    "use strict";

    const REFRESH_INTERVAL_MS = 5000;
    const dateFormatter = new Intl.DateTimeFormat("zh-CN", {
        year: "numeric",
        month: "2-digit",
        day: "2-digit",
        hour: "2-digit",
        minute: "2-digit",
        second: "2-digit",
        hour12: false
    });

    const elements = {};
    let refreshing = false;

    document.addEventListener("DOMContentLoaded", initialize);

    function initialize() {
        elements.refreshButton = document.getElementById("refresh-button");
        elements.healthBadge = document.getElementById("health-badge");
        elements.healthStatus = document.getElementById("health-status");
        elements.notice = document.getElementById("notice");
        elements.applicationCount = document.getElementById("application-count");
        elements.instanceCount = document.getElementById("instance-count");
        elements.onlineCount = document.getElementById("online-count");
        elements.offlineCount = document.getElementById("offline-count");
        elements.lastRefreshed = document.getElementById("last-refreshed");
        elements.tableWrap = document.getElementById("table-wrap");
        elements.tableBody = document.getElementById("instance-table-body");
        elements.emptyState = document.getElementById("empty-state");
        elements.startedAt = document.getElementById("started-at");
        elements.uptime = document.getElementById("uptime");

        elements.refreshButton.addEventListener("click", refreshDashboard);
        updateRuntimeInformation();
        refreshDashboard();
        window.setInterval(refreshDashboard, REFRESH_INTERVAL_MS);
        window.setInterval(updateRuntimeInformation, 1000);
    }

    async function refreshDashboard() {
        if (refreshing) {
            return;
        }

        setRefreshing(true);
        hideNotice();

        try {
            const [healthResponse, applicationsResponse] = await Promise.all([
                fetch("/actuator/health", {headers: {Accept: "application/json"}}),
                fetch("/api/v1/applications", {headers: {Accept: "application/json"}})
            ]);

            if (!healthResponse.ok) {
                throw new Error(`健康接口返回 ${healthResponse.status}`);
            }
            if (!applicationsResponse.ok) {
                throw new Error(`注册信息接口返回 ${applicationsResponse.status}`);
            }

            const health = await healthResponse.json();
            const applications = await applicationsResponse.json();
            if (!Array.isArray(applications)) {
                throw new Error("注册信息接口返回了无法识别的数据格式");
            }

            renderHealth(health.status);
            renderRegistry(applications);
            elements.lastRefreshed.textContent = dateFormatter.format(new Date());
        } catch (error) {
            renderHealth("DOWN");
            showNotice(`实时数据刷新失败：${error.message}`);
        } finally {
            setRefreshing(false);
        }
    }

    function renderHealth(status) {
        const normalized = String(status || "UNKNOWN").toUpperCase();
        elements.healthStatus.textContent = normalized;
        elements.healthBadge.classList.remove("health-checking", "health-up", "health-down");
        elements.healthBadge.classList.add(normalized === "UP" ? "health-up" : "health-down");
    }

    function renderRegistry(applications) {
        const instances = applications.flatMap(application =>
            (Array.isArray(application.instances) ? application.instances : []).map(instance => ({
                ...instance,
                applicationCode: instance.applicationCode || application.applicationCode,
                applicationName: instance.applicationName || application.applicationName
            }))
        );

        const onlineCount = instances.filter(instance => instance.status === "ONLINE").length;
        const offlineCount = instances.filter(instance => instance.status === "OFFLINE").length;

        elements.applicationCount.textContent = String(applications.length);
        elements.instanceCount.textContent = String(instances.length);
        elements.onlineCount.textContent = String(onlineCount);
        elements.offlineCount.textContent = String(offlineCount);

        elements.tableBody.replaceChildren();
        elements.tableWrap.hidden = instances.length === 0;
        elements.emptyState.hidden = instances.length > 0;

        if (instances.length === 0) {
            return;
        }

        const fragment = document.createDocumentFragment();
        instances.forEach(instance => fragment.appendChild(createInstanceRow(instance)));
        elements.tableBody.appendChild(fragment);
    }

    function createInstanceRow(instance) {
        const row = document.createElement("tr");
        row.appendChild(createPrimarySecondaryCell(instance.applicationName, instance.applicationCode));
        row.appendChild(createPrimarySecondaryCell(instance.instanceId, `注册于 ${formatDate(instance.registeredAt)}`));
        row.appendChild(createStatusCell(instance.status));
        row.appendChild(createUrlCell(instance.baseUrl));
        row.appendChild(createTextCell(instance.version || "—"));
        row.appendChild(createTextCell(formatDate(instance.lastHeartbeatAt)));
        row.appendChild(createMetadataCell(instance.metadata));
        return row;
    }

    function createPrimarySecondaryCell(primary, secondary) {
        const cell = document.createElement("td");
        const primaryElement = document.createElement("span");
        const secondaryElement = document.createElement("span");
        primaryElement.className = "primary-cell";
        secondaryElement.className = "secondary-cell";
        primaryElement.textContent = primary || "—";
        secondaryElement.textContent = secondary || "—";
        cell.append(primaryElement, secondaryElement);
        return cell;
    }

    function createStatusCell(status) {
        const normalized = String(status || "UNKNOWN").toUpperCase();
        const cell = document.createElement("td");
        const badge = document.createElement("span");
        badge.className = `status-badge ${normalized === "ONLINE" ? "status-online" : "status-offline"}`;
        badge.textContent = normalized;
        cell.appendChild(badge);
        return cell;
    }

    function createUrlCell(baseUrl) {
        const cell = document.createElement("td");
        if (!baseUrl) {
            cell.textContent = "—";
            return cell;
        }

        const link = document.createElement("a");
        link.className = "service-link";
        link.href = baseUrl;
        link.target = "_blank";
        link.rel = "noreferrer";
        link.textContent = baseUrl;
        cell.appendChild(link);
        return cell;
    }

    function createMetadataCell(metadata) {
        const cell = document.createElement("td");
        const entries = metadata && typeof metadata === "object" ? Object.entries(metadata) : [];
        if (entries.length === 0) {
            cell.textContent = "—";
            return cell;
        }

        const list = document.createElement("div");
        list.className = "metadata-list";
        entries.forEach(([key, value]) => {
            const item = document.createElement("span");
            item.className = "metadata-item";
            item.textContent = `${key}=${value}`;
            item.title = item.textContent;
            list.appendChild(item);
        });
        cell.appendChild(list);
        return cell;
    }

    function createTextCell(value) {
        const cell = document.createElement("td");
        cell.textContent = value;
        return cell;
    }

    function updateRuntimeInformation() {
        const startedAt = new Date(document.body.dataset.startedAt);
        if (Number.isNaN(startedAt.getTime())) {
            elements.startedAt.textContent = "未知";
            elements.uptime.textContent = "未知";
            return;
        }

        elements.startedAt.textContent = dateFormatter.format(startedAt);
        elements.startedAt.title = startedAt.toISOString();
        elements.uptime.textContent = formatDuration(Date.now() - startedAt.getTime());
    }

    function formatDate(value) {
        if (!value) {
            return "—";
        }
        const date = new Date(value);
        return Number.isNaN(date.getTime()) ? String(value) : dateFormatter.format(date);
    }

    function formatDuration(milliseconds) {
        const totalSeconds = Math.max(0, Math.floor(milliseconds / 1000));
        const days = Math.floor(totalSeconds / 86400);
        const hours = Math.floor((totalSeconds % 86400) / 3600);
        const minutes = Math.floor((totalSeconds % 3600) / 60);
        const seconds = totalSeconds % 60;

        if (days > 0) {
            return `${days}天 ${hours}小时 ${minutes}分`;
        }
        if (hours > 0) {
            return `${hours}小时 ${minutes}分 ${seconds}秒`;
        }
        return `${minutes}分 ${seconds}秒`;
    }

    function setRefreshing(value) {
        refreshing = value;
        elements.refreshButton.disabled = value;
        elements.refreshButton.classList.toggle("is-loading", value);
    }

    function showNotice(message) {
        elements.notice.textContent = message;
        elements.notice.hidden = false;
    }

    function hideNotice() {
        elements.notice.hidden = true;
        elements.notice.textContent = "";
    }
})();
