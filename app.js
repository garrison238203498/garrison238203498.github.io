const scene = document.getElementById("scene");
const sceneObjects = document.getElementById("sceneObjects");
const gasReadout = document.getElementById("gasReadout");
const timeValue = document.getElementById("timeValue");
const scoreValue = document.getElementById("scoreValue");
const roomName = document.getElementById("roomName");
const roomHazard = document.getElementById("roomHazard");
const roomSummary = document.getElementById("roomSummary");
const stationMap = document.getElementById("stationMap");
const movementGrid = document.getElementById("movementGrid");
const routeList = document.getElementById("routeList");
const warningList = document.getElementById("warningList");
const inventoryGrid = document.getElementById("inventoryGrid");
const logPanel = document.getElementById("logPanel");
const commandForm = document.getElementById("commandForm");
const commandInput = document.getElementById("commandInput");
const pendingBadge = document.getElementById("pendingBadge");
const resetButton = document.getElementById("resetButton");
const choiceOverlay = document.getElementById("choiceOverlay");
const choicePrompt = document.getElementById("choicePrompt");
const choiceA = document.getElementById("choiceA");
const choiceB = document.getElementById("choiceB");

let state = null;

const sceneLayouts = {
    AIRLOCK: {
        mask: { x: "64%", y: "30%", w: "12%", h: "24%" }
    },
    OBSERVATION: {
        monitor: { x: "18%", y: "20%", w: "30%", h: "22%" },
        "relay-coil": { x: "62%", y: "44%", w: "11%", h: "12%" }
    },
    COMMAND: {
        "main-door": { x: "34%", y: "16%", w: "28%", h: "52%" },
        logs: { x: "72%", y: "28%", w: "14%", h: "22%" }
    },
    JUNCTION: {
        "route-map": { x: "55%", y: "20%", w: "22%", h: "20%" }
    },
    RELAY: {
        panel: { x: "20%", y: "18%", w: "28%", h: "34%" },
        "panel-interior": { x: "24%", y: "22%", w: "18%", h: "22%" }
    },
    POD_BAY: {
        pod: { x: "45%", y: "14%", w: "32%", h: "54%" },
        "pod-socket": { x: "20%", y: "44%", w: "14%", h: "16%" }
    },
    MAINTENANCE: {
        toolbox: { x: "24%", y: "56%", w: "20%", h: "12%" }
    },
    FILTRATION: {
        grate: { x: "18%", y: "38%", w: "24%", h: "20%" },
        "filter-shelf": { x: "58%", y: "26%", w: "20%", h: "22%" },
        "spare-fuse": { x: "66%", y: "52%", w: "8%", h: "10%" },
        "filter-patch": { x: "54%", y: "56%", w: "10%", h: "12%" }
    },
    SECURITY: {
        safe: { x: "26%", y: "24%", w: "16%", h: "22%" },
        locker: { x: "62%", y: "18%", w: "16%", h: "42%" }
    }
};

const directionLabels = {
    north: "North",
    south: "South",
    east: "East",
    west: "West"
};

async function fetchState() {
    const response = await fetch("/api/state");
    state = await response.json();
    render();
}

async function submitInput(input) {
    const response = await fetch("/api/input", {
        method: "POST",
        headers: { "Content-Type": "text/plain;charset=UTF-8" },
        body: input
    });

    state = await response.json();
    render();
}

async function resetGame() {
    const response = await fetch("/api/reset", { method: "POST" });
    state = await response.json();
    render();
}

function render() {
    if (!state) {
        return;
    }

    renderMetrics();
    renderRoom();
    renderMap();
    renderMovement();
    renderRoutes();
    renderWarnings();
    renderInventory();
    renderLog();
    renderPendingState();
}

function renderMetrics() {
    const gasPercent = Math.max(0, Math.min(100, Math.round(((state.timeLimit - state.timeLeft) / state.timeLimit) * 100)));
    gasReadout.textContent = `${gasPercent}%`;
    timeValue.textContent = `${state.timeLeft}s`;
    scoreValue.textContent = `${state.score}`;
}

function renderRoom() {
    roomName.textContent = state.currentRoomName;
    roomHazard.textContent = `${state.currentRoomHazard} Hazard`;
    roomSummary.textContent = state.currentRoomSummary;
    scene.dataset.room = state.currentRoomId;

    const classes = [
        "scene",
        state.lowTime ? "low-time" : "",
        state.panelOpened ? "panel-open" : "",
        state.powerRestored ? "power-restored" : "",
        state.grateOpened ? "grate-open" : "",
        state.podPowered ? "pod-powered" : "",
        state.gameOver ? "game-over" : ""
    ].filter(Boolean);
    scene.className = classes.join(" ");

    const gasPercent = Math.max(8, Math.min(92, Math.round(((state.timeLimit - state.timeLeft) / state.timeLimit) * 78) + 8));
    scene.style.setProperty("--gas-level", `${gasPercent}%`);

    const layout = sceneLayouts[state.currentRoomId] || {};
    sceneObjects.innerHTML = state.visibleObjects.map((object) => {
        const box = layout[object.id] || { x: "42%", y: "42%", w: "14%", h: "14%" };
        const tag = object.interactive ? "button" : "div";
        const commandAttr = object.interactive ? `data-command="${escapeHtml(object.command)}"` : "";
        const disabledAttr = object.interactive ? `type="button"` : "";
        return `
            <${tag}
                class="scene-object type-${object.type}${object.interactive ? " interactive" : ""}"
                data-id="${object.id}"
                ${commandAttr}
                ${disabledAttr}
                style="left:${box.x};top:${box.y};width:${box.w};height:${box.h};"
            >
                <span>${escapeHtml(object.label)}</span>
            </${tag}>
        `;
    }).join("");

    sceneObjects.querySelectorAll("[data-command]").forEach((button) => {
        button.addEventListener("click", () => {
            if (!state.gameOver) {
                submitInput(button.dataset.command);
            }
        });
    });
}

function renderMap() {
    const cells = [...state.rooms].sort((a, b) => (a.y - b.y) || (a.x - b.x));
    stationMap.innerHTML = cells.map((room) => `
        <div class="map-cell${room.current ? " current" : ""}${room.visited ? " visited" : ""}">
            <strong>${escapeHtml(room.name)}</strong>
            <span>${escapeHtml(room.hazard)}</span>
        </div>
    `).join("");
}

function renderMovement() {
    const moves = ["north", "west", "east", "south"];
    movementGrid.innerHTML = moves.map((direction) => {
        const enabled = state.availableDirections.includes(direction);
        return `
            <button class="move-button${enabled ? "" : " disabled"}" type="button" data-direction="${direction}" ${enabled ? "" : "disabled"}>
                ${directionLabels[direction]}
            </button>
        `;
    }).join("");

    movementGrid.querySelectorAll("[data-direction]").forEach((button) => {
        button.addEventListener("click", () => {
            if (!state.gameOver && !button.disabled) {
                submitInput(`go ${button.dataset.direction}`);
            }
        });
    });
}

function renderRoutes() {
    const inventory = new Set(state.inventory);
    const routes = [
        {
            name: "Main Door",
            detail: state.innerDoorUnlocked
                ? inventory.has("main door code") ? "Command Lock ready for final solve." : "Door unlocked, final code still missing."
                : "Solve the relay override in Systems Relay first."
        },
        {
            name: "Vent Route",
            detail: state.ventRouteOpen
                ? "Filtration Access vent is open."
                : state.grateOpened ? "Grate is open, inner blockage remains." : "Vent still sealed in Filtration Access."
        },
        {
            name: "Escape Pod",
            detail: state.podPowered
                ? inventory.has("launch key") ? "Pod bay ready for launch." : "Pod online, launch key still missing."
                : state.relayCoilInstalled ? "Relay installed. Battery still needed." : "Restore relay power and install pod coil."
        }
    ];

    routeList.innerHTML = routes.map((route) => `
        <article class="route-item">
            <strong>${route.name}</strong>
            <div>${route.detail}</div>
        </article>
    `).join("");
}

function renderWarnings() {
    const inventory = new Set(state.inventory);
    const warnings = [];

    warnings.push({
        title: `${state.currentRoomHazard} Room`,
        detail: `${state.currentRoomName} is your current position in the 3x3 block.`
    });

    if (state.lowTime) {
        warnings.push({
            title: "Gas Critical",
            detail: "The toxic layer is climbing fast. Route efficiency matters now."
        });
    }

    if (!inventory.has("oxygen mask")) {
        warnings.push({
            title: "Mask Missing",
            detail: "The safest opening move is still sitting back in the Airlock Chamber."
        });
    } else if (!state.maskEquipped) {
        warnings.push({
            title: "Mask Unequipped",
            detail: "You have the mask, but high-hazard rooms still punish movement until it is on."
        });
    }

    if (inventory.has("filter patch") && !state.filterPatchUsed) {
        warnings.push({
            title: "Patch Unused",
            detail: "The filter patch can reinforce the oxygen mask before a risky run."
        });
    }

    if (state.gameOver) {
        warnings.push({
            title: state.escaped ? "Escape Confirmed" : "Run Lost",
            detail: state.escaped ? `Route confirmed: ${state.escapeRoute}.` : "The station section won the race."
        });
    }

    warningList.innerHTML = warnings.map((warning) => `
        <article class="warning-item">
            <strong>${warning.title}</strong>
            <div>${warning.detail}</div>
        </article>
    `).join("");
}

function renderInventory() {
    if (!state.inventory.length) {
        inventoryGrid.innerHTML = `<div class="warning-item">Inventory empty.</div>`;
        return;
    }

    inventoryGrid.innerHTML = state.inventory.map((item) => {
        let status = "Ready";
        if (item === "oxygen mask" && state.maskEquipped) {
            status = state.filterPatchUsed ? "Equipped + Reinforced" : "Equipped";
        }
        return `
            <button class="inventory-chip" type="button" data-item="${item}">
                ${formatItem(item)}
                <span>${status}</span>
            </button>
        `;
    }).join("");

    inventoryGrid.querySelectorAll("[data-item]").forEach((button) => {
        button.addEventListener("click", () => {
            if (!state.gameOver) {
                submitInput(`use ${button.dataset.item}`);
            }
        });
    });
}

function renderLog() {
    logPanel.innerHTML = state.log.map((line) => {
        if (!line) {
            return `<p class="log-line muted">&nbsp;</p>`;
        }
        const muted = line.startsWith("Commands:") || line.startsWith("          ");
        return `<p class="log-line${muted ? " muted" : ""}">${escapeHtml(line)}</p>`;
    }).join("");

    logPanel.scrollTop = logPanel.scrollHeight;
}

function renderPendingState() {
    if (state.pendingRequest === "NONE") {
        pendingBadge.textContent = state.gameOver ? (state.escaped ? "Escape Confirmed" : "Run Ended") : "Ready";
        choiceOverlay.classList.add("hidden");
        commandInput.placeholder = "Type a command: go east";
        commandInput.disabled = state.gameOver;
        return;
    }

    pendingBadge.textContent = state.pendingChoice ? "Decision Required" : "Code Required";
    commandInput.disabled = false;

    if (state.pendingChoice) {
        choicePrompt.textContent = state.pendingChoice.prompt;
        choiceA.textContent = state.pendingChoice.optionA;
        choiceB.textContent = state.pendingChoice.optionB;
        choiceOverlay.classList.remove("hidden");
        commandInput.placeholder = "Decision overlay active";
    } else {
        choiceOverlay.classList.add("hidden");
        commandInput.placeholder = state.pendingPrompt || "Awaiting input";
    }
}

function formatItem(item) {
    return item.replace(/\b\w/g, (char) => char.toUpperCase());
}

function escapeHtml(value) {
    return value
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;");
}

document.querySelectorAll("[data-command]").forEach((button) => {
    button.addEventListener("click", () => {
        if (!state || state.gameOver) {
            return;
        }
        submitInput(button.dataset.command);
    });
});

choiceA.addEventListener("click", () => submitInput("A"));
choiceB.addEventListener("click", () => submitInput("B"));

commandForm.addEventListener("submit", (event) => {
    event.preventDefault();
    if (!state || state.gameOver) {
        return;
    }

    const value = commandInput.value.trim();
    if (!value) {
        return;
    }

    commandInput.value = "";
    submitInput(value);
});

resetButton.addEventListener("click", resetGame);

fetchState().catch((error) => {
    logPanel.innerHTML = `<p class="log-line">Failed to load game state: ${escapeHtml(String(error))}</p>`;
});
