import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

public class Game {
    private static final int TIME_LIMIT = 600;
    private static final String AIRLOCK = "AIRLOCK";
    private static final String OBSERVATION = "OBSERVATION";
    private static final String COMMAND = "COMMAND";
    private static final String JUNCTION = "JUNCTION";
    private static final String RELAY = "RELAY";
    private static final String POD_BAY = "POD_BAY";
    private static final String MAINTENANCE = "MAINTENANCE";
    private static final String FILTRATION = "FILTRATION";
    private static final String SECURITY = "SECURITY";

    private final Scanner scanner;
    private final Player player;
    private final CommandParser parser;
    private final TextBank text;
    private final List<String> logLines;
    private final Map<String, Room> rooms;
    private final Set<String> visitedRooms;

    private long startTime;
    private boolean escaped;
    private boolean gasWarningShown;
    private boolean started;
    private boolean gameOver;
    private boolean consoleMode;

    private String currentRoomId;
    private String escapeRoute;

    private boolean grateOpened;
    private boolean ventRouteOpen;
    private boolean toolboxOpened;
    private boolean safeOpened;
    private boolean lockerOpened;

    private boolean panelOpened;
    private boolean powerRestored;
    private boolean innerDoorUnlocked;

    private boolean podRouteUnlocked;
    private boolean podPowered;
    private boolean relayCoilVisible;
    private boolean relayCoilInstalled;

    private boolean spareFuseVisible;
    private boolean filterPatchVisible;
    private boolean filterPatchUsed;

    private boolean toolboxChoiceMade;
    private boolean rushedToolbox;

    private boolean maskChoiceMade;
    private boolean savedMaskForLater;

    private boolean ventChoiceMade;
    private boolean tookUpperVent;

    private boolean podChoiceMade;
    private boolean inspectedPodSystems;

    private boolean finalChoiceMade;
    private boolean savedStationLogs;

    private PendingRequestType pendingRequest;
    private Choice pendingChoice;
    private String pendingPrompt;

    public Game() {
        scanner = new Scanner(System.in);
        player = new Player();
        parser = new CommandParser();
        text = new TextBank("phrases.txt");
        logLines = new ArrayList<>();
        rooms = buildRooms();
        visitedRooms = new LinkedHashSet<>();

        escaped = false;
        gasWarningShown = false;
        started = false;
        gameOver = false;
        consoleMode = false;
        currentRoomId = AIRLOCK;
        escapeRoute = "";
        pendingRequest = PendingRequestType.NONE;
        pendingPrompt = "";
    }

    public void start() {
        consoleMode = true;
        begin();

        while (!gameOver && !escaped) {
            ensureTimeState();
            if (gameOver || escaped) {
                break;
            }

            if (pendingRequest == PendingRequestType.NONE) {
                println("");
                println("[" + getCurrentRoom().getName() + " | Time left: " + getTimeLeft()
                    + " seconds | Score: " + player.getScore() + "]");
            }

            System.out.print("> ");
            if (!scanner.hasNextLine()) {
                break;
            }

            submitInput(scanner.nextLine().trim());
        }

        scanner.close();
    }

    // Bridge point: the JS frontend drives the existing game one input at a time.
    public synchronized void submitInput(String input) {
        begin();
        ensureTimeState();

        if (gameOver || escaped) {
            return;
        }

        String normalizedInput = input == null ? "" : input.trim();

        if (pendingRequest != PendingRequestType.NONE) {
            resolvePendingInput(normalizedInput);
        } else {
            Command command = parser.parse(normalizedInput);
            handleCommand(command);
        }

        ensureTimeState();
    }

    public synchronized void begin() {
        if (started) {
            return;
        }

        started = true;
        startTime = System.currentTimeMillis();
        visitedRooms.add(currentRoomId);
        printIntro();
        lookAround();
    }

    // Bridge point: the web server serializes this state directly to the JS UI.
    public synchronized String toWebStateJson() {
        ensureTimeState();

        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"started\":").append(started).append(",");
        json.append("\"escaped\":").append(escaped).append(",");
        json.append("\"gameOver\":").append(gameOver).append(",");
        json.append("\"escapeRoute\":\"").append(escapeJson(escapeRoute)).append("\",");
        json.append("\"timeLimit\":").append(TIME_LIMIT).append(",");
        json.append("\"timeLeft\":").append(started ? getTimeLeft() : TIME_LIMIT).append(",");
        json.append("\"score\":").append(player.getScore()).append(",");
        json.append("\"maskEquipped\":").append(player.isMaskEquipped()).append(",");
        json.append("\"filterPatchUsed\":").append(filterPatchUsed).append(",");
        json.append("\"lowTime\":").append(started && getTimeLeft() <= 180).append(",");
        json.append("\"inventory\":").append(toJsonArray(player.getInventoryNames())).append(",");
        json.append("\"pendingRequest\":\"").append(pendingRequest.name()).append("\",");
        json.append("\"pendingPrompt\":\"").append(escapeJson(pendingPrompt)).append("\",");
        json.append("\"pendingChoice\":").append(toChoiceJson()).append(",");
        json.append("\"currentRoomId\":\"").append(escapeJson(currentRoomId)).append("\",");
        json.append("\"currentRoomName\":\"").append(escapeJson(getCurrentRoom().getName())).append("\",");
        json.append("\"currentRoomHazard\":\"").append(escapeJson(getCurrentRoom().getHazard().getLabel())).append("\",");
        json.append("\"currentRoomSummary\":\"").append(escapeJson(getRoomSummary(getCurrentRoom()))).append("\",");
        json.append("\"availableDirections\":").append(directionsToJson()).append(",");
        json.append("\"rooms\":").append(roomsToJson()).append(",");
        json.append("\"visibleObjects\":").append(sceneObjectsToJson(getVisibleObjectsForCurrentRoom())).append(",");
        json.append("\"log\":").append(toJsonArray(logLines)).append(",");
        json.append("\"grateOpened\":").append(grateOpened).append(",");
        json.append("\"ventRouteOpen\":").append(ventRouteOpen).append(",");
        json.append("\"toolboxOpened\":").append(toolboxOpened).append(",");
        json.append("\"safeOpened\":").append(safeOpened).append(",");
        json.append("\"lockerOpened\":").append(lockerOpened).append(",");
        json.append("\"panelOpened\":").append(panelOpened).append(",");
        json.append("\"powerRestored\":").append(powerRestored).append(",");
        json.append("\"innerDoorUnlocked\":").append(innerDoorUnlocked).append(",");
        json.append("\"podRouteUnlocked\":").append(podRouteUnlocked).append(",");
        json.append("\"podPowered\":").append(podPowered).append(",");
        json.append("\"relayCoilVisible\":").append(relayCoilVisible).append(",");
        json.append("\"relayCoilInstalled\":").append(relayCoilInstalled).append(",");
        json.append("\"spareFuseVisible\":").append(spareFuseVisible).append(",");
        json.append("\"filterPatchVisible\":").append(filterPatchVisible).append(",");
        json.append("\"toolboxChoiceMade\":").append(toolboxChoiceMade).append(",");
        json.append("\"rushedToolbox\":").append(rushedToolbox).append(",");
        json.append("\"maskChoiceMade\":").append(maskChoiceMade).append(",");
        json.append("\"savedMaskForLater\":").append(savedMaskForLater).append(",");
        json.append("\"ventChoiceMade\":").append(ventChoiceMade).append(",");
        json.append("\"tookUpperVent\":").append(tookUpperVent).append(",");
        json.append("\"podChoiceMade\":").append(podChoiceMade).append(",");
        json.append("\"inspectedPodSystems\":").append(inspectedPodSystems).append(",");
        json.append("\"finalChoiceMade\":").append(finalChoiceMade).append(",");
        json.append("\"savedStationLogs\":").append(savedStationLogs);
        json.append("}");
        return json.toString();
    }

    private Map<String, Room> buildRooms() {
        Map<String, Room> map = new LinkedHashMap<>();
        map.put(OBSERVATION, new Room(OBSERVATION, "Observation Nook", 0, 0, HazardLevel.LOW,
            "Thin monitor-light bleeds through cracked glass and reflective trim."));
        map.put(COMMAND, new Room(COMMAND, "Command Lock", 1, 0, HazardLevel.MEDIUM,
            "The main lock chamber sits above the junction with the station exit sealed ahead."));
        map.put(POD_BAY, new Room(POD_BAY, "Escape Pod Bay", 2, 0, HazardLevel.MEDIUM,
            "The emergency pod cradle waits in the dark upper bay."));
        map.put(AIRLOCK, new Room(AIRLOCK, "Airlock Chamber", 0, 1, HazardLevel.LOW,
            "The ruptured service airlock is the calmest corner left in this section."));
        map.put(JUNCTION, new Room(JUNCTION, "Central Junction", 1, 1, HazardLevel.MEDIUM,
            "This crossing ties the whole damaged block together."));
        map.put(RELAY, new Room(RELAY, "Systems Relay", 2, 1, HazardLevel.HIGH,
            "Hot metal, sparks, and the relay control panel dominate the room."));
        map.put(MAINTENANCE, new Room(MAINTENANCE, "Maintenance Bay", 0, 2, HazardLevel.HIGH,
            "Tool racks and sealed trays crowd this lower maintenance bay."));
        map.put(FILTRATION, new Room(FILTRATION, "Filtration Access", 1, 2, HazardLevel.HIGH,
            "A service grate and emergency filter shelf choke the wall here."));
        map.put(SECURITY, new Room(SECURITY, "Security Annex", 2, 2, HazardLevel.HIGH,
            "A wall safe and sealed locker sit in a greener, hotter corner of the station."));
        return map;
    }

    private void resolvePendingInput(String input) {
        switch (pendingRequest) {
            case SAFE_CODE:
                handleSafeCode(input);
                break;
            case OVERRIDE_CODE:
                handleOverrideCode(input);
                break;
            case MAIN_CODE:
                handleMainDoorCode(input);
                break;
            case TOOLBOX_CHOICE:
                handleToolboxChoiceResponse(input);
                break;
            case MASK_CHOICE:
                handleMaskChoiceResponse(input);
                break;
            case VENT_CHOICE:
                handleVentChoiceResponse(input);
                break;
            case POD_CHOICE:
                handlePodChoiceResponse(input);
                break;
            case FINAL_CHOICE:
                handleFinalChoiceResponse(input);
                break;
            default:
                clearPendingRequest();
                break;
        }
    }

    private void handleSafeCode(String input) {
        clearPendingRequest();

        if (input.equals("247")) {
            println(text.get("SAFE_2"));
            println(text.get("SAFE_3"));
            player.addItem(new Item("access card"));
            player.addItem(new Item("main door code"));
            safeOpened = true;
            player.addScore(20);
        } else {
            println(text.get("SAFE_4") + " The filter-bank tags around the section keep repeating the same three numbers.");
        }
    }

    private void handleOverrideCode(String input) {
        clearPendingRequest();

        if (input.equals("3917")) {
            innerDoorUnlocked = true;
            println(text.get("SOLVE_PANEL_3"));
            println(text.get("SOLVE_PANEL_4"));
            player.addScore(25);
        } else {
            println(text.get("SOLVE_PANEL_5") + " The reflected digits in the relay casing were four numbers long.");
        }
    }

    private void handleMainDoorCode(String input) {
        clearPendingRequest();

        if (input.equals("5193")) {
            escapeSuccess("MAIN DOOR");
        } else {
            println(text.get("SOLVE_MAIN_3") + " The code strip from the safe still has four digits on it.");
        }
    }

    private void handleToolboxChoiceResponse(String input) {
        String choice = normalizeChoice(input);
        if (choice == null) {
            println("Choose A or B.");
            return;
        }

        clearPendingRequest();
        toolboxChoiceMade = true;
        toolboxOpened = true;
        player.addItem(new Item("screwdriver"));
        player.addItem(new Item("crowbar"));

        if (choice.equals("A")) {
            rushedToolbox = true;
            println("You snatch the obvious tools and leave the false bottom alone.");
            println("Fast, but if this bay hid anything subtle, you missed it.");
            player.addScore(10);
        } else {
            rushedToolbox = false;
            consumeTime(12);
            println("You peel back the false bottom and burn precious seconds doing it.");
            println("The extra search pays off with a replacement fuse and a filter patch.");
            player.addItem(new Item("fuse"));
            player.addItem(new Item("filter patch"));
            player.addScore(16);
        }
    }

    private void handleMaskChoiceResponse(String input) {
        String choice = normalizeChoice(input);
        if (choice == null) {
            println("Choose A or B.");
            return;
        }

        clearPendingRequest();
        maskChoiceMade = true;
        player.addItem(new Item("oxygen mask"));

        if (choice.equals("A")) {
            savedMaskForLater = false;
            player.setMaskEquipped(true);
            println(text.get("USE_MASK_1"));
            println(text.get("USE_MASK_2"));
            player.addScore(10);
        } else {
            savedMaskForLater = true;
            println("You keep the mask in hand and gamble on a few quick moves first.");
            println("That saves a little freedom now, but the lower rooms will punish the risk.");
            player.addScore(5);
        }
    }

    private void handleVentChoiceResponse(String input) {
        String choice = normalizeChoice(input);
        if (choice == null) {
            println("Choose A or B.");
            return;
        }

        clearPendingRequest();
        ventChoiceMade = true;

        if (choice.equals("A")) {
            tookUpperVent = true;
            consumeTime(8);
            println("You climb for the upper duct and drag yourself through sharp brackets and dust.");
            println("It is slower, but the cleaner air keeps the route survivable.");
            player.addScore(20);
        } else {
            tookUpperVent = false;
            println("You drop into the lower shaft where the gas sits thickest.");
            if (!player.isMaskEquipped()) {
                println("Without the mask, the leak tears at your lungs and costs you time.");
                consumeTime(25);
            } else if (!filterPatchUsed) {
                println("The mask saves you, but the damaged seal still leaks enough to slow you.");
                consumeTime(8);
            } else {
                println("The reinforced mask keeps the worst of the poison out.");
            }
            player.addScore(15);
        }

        println(text.get("ESCAPE_VENT_3"));
        escapeSuccess("VENT SHAFT");
    }

    private void handlePodChoiceResponse(String input) {
        String choice = normalizeChoice(input);
        if (choice == null) {
            println("Choose A or B.");
            return;
        }

        clearPendingRequest();
        podChoiceMade = true;

        if (choice.equals("A")) {
            inspectedPodSystems = true;
            consumeTime(6);
            println("You run the pod diagnostics instead of trusting the first green light.");
            println("It costs time, but the launch stack settles into a safer rhythm.");
            player.addScore(15);
        } else {
            inspectedPodSystems = false;
            println("You skip the deeper checks and commit to the launch immediately.");
            println("Fast, but reckless.");
            player.addScore(10);
        }

        println(text.get("ESCAPE_POD_3"));
        escapeSuccess("ESCAPE POD");
    }

    private void handleFinalChoiceResponse(String input) {
        String choice = normalizeChoice(input);
        if (choice == null) {
            println("Choose A or B.");
            return;
        }

        clearPendingRequest();
        finalChoiceMade = true;

        if (choice.equals("A")) {
            savedStationLogs = true;
            consumeTime(8);
            println("You archive the station logs before you leave.");
            println("It costs precious seconds, but the whole disaster will not vanish with the gas.");
            player.addScore(20);
        } else {
            savedStationLogs = false;
            println("You leave the logs behind and focus on surviving the next minute.");
            player.addScore(5);
        }

        requestCode(PendingRequestType.MAIN_CODE, "Enter the main door code:");
    }

    private void handleCommand(Command command) {
        CommandType type = command.getType();
        String target = command.getTarget();

        switch (type) {
            case LOOK:
                lookAround();
                break;
            case MOVE:
                move(target);
                break;
            case MAP:
                showMap();
                break;
            case HELP:
                showHelp();
                break;
            case INVENTORY:
                player.showInventory(this::println);
                break;
            case STATUS:
                showStatus();
                break;
            case EXAMINE:
                examine(target);
                break;
            case USE:
                useItem(target);
                break;
            case SOLVE:
                solve(target);
                break;
            case ESCAPE:
                escape(target);
                break;
            case QUIT:
                println(text.get("QUIT_1"));
                gameOver = true;
                break;
            default:
                println("That doesn't seem useful right now. Try `look`, `map`, or `go east`.");
                break;
        }
    }

    private void printIntro() {
        println("========================================");
        println("       POISON AIRLOCK ESCAPE");
        println("========================================");
        println(text.get("INTRO_1"));
        println(text.get("INTRO_2"));
        println(text.get("INTRO_3"));
        println(text.get("INTRO_4"));
        println(text.get("INTRO_5"));
        println("");
        println("Commands: look, go [north/south/east/west], map, examine [object],");
        println("          use [item], solve [thing], escape [main / vent / pod],");
        println("          inventory, status, help, quit");
        println("========================================");
    }

    public synchronized int getTimeLeft() {
        long elapsed = started ? (System.currentTimeMillis() - startTime) / 1000 : 0;
        int timeLeft = TIME_LIMIT - (int) elapsed;

        if (player.isMaskEquipped()) {
            timeLeft += 30;
        }
        if (filterPatchUsed) {
            timeLeft += 20;
        }

        return Math.max(0, timeLeft);
    }

    private void ensureTimeState() {
        if (!started || gameOver || escaped) {
            return;
        }

        int timeLeft = getTimeLeft();

        if (timeLeft <= 0) {
            println("");
            println(text.get("GAME_OVER_1"));
            println(text.get("GAME_OVER_2"));
            println("Final Score: " + player.getScore());
            gameOver = true;
            clearPendingRequest();
            return;
        }

        if (timeLeft <= 180 && !gasWarningShown) {
            println("");
            println("*** " + text.getRandom("LOW_TIME_WARNING") + " ***");
            gasWarningShown = true;
        }
    }
    private void lookAround() {
        Room room = getCurrentRoom();
        println("");
        println("[" + room.getName() + "]");
        println(getRoomSummary(room));
        println(getRoomDetail(room));

        List<SceneObject> objects = getVisibleObjectsForCurrentRoom();
        if (objects.isEmpty()) {
            println("Nothing here is immediately usable, but the exits still matter.");
        } else {
            println("You can focus on:");
            for (SceneObject object : objects) {
                println("- " + object.label);
            }
        }

        println("Exits: " + exitsText());
    }

    private void move(String rawDirection) {
        Direction direction = Direction.fromText(rawDirection);
        if (direction == null) {
            println("Move where? Try north, south, east, or west.");
            return;
        }

        Room nextRoom = getNeighbor(direction);
        if (nextRoom == null) {
            println("There is no clear path " + direction.getLabel() + " from here.");
            return;
        }

        consumeTime(3);
        currentRoomId = nextRoom.getId();
        visitedRooms.add(currentRoomId);

        if (!player.isMaskEquipped()) {
            if (nextRoom.getHazard() == HazardLevel.HIGH) {
                println("The thicker gas in " + nextRoom.getName() + " burns your lungs and steals more time.");
                consumeTime(8);
            } else if (nextRoom.getHazard() == HazardLevel.MEDIUM) {
                println("The air gets rougher here. Without the mask, even moving costs you.");
                consumeTime(3);
            }
        }

        println("You move " + direction.getLabel() + " into " + nextRoom.getName() + ".");
        lookAround();
    }

    private void examine(String rawTarget) {
        String target = parser.normalizeObject(rawTarget);

        if (target.isEmpty()) {
            println("Examine what?");
            return;
        }

        if (player.hasItem(parser.normalizeItem(target))) {
            examineInventoryItem(parser.normalizeItem(target));
            return;
        }

        switch (target) {
            case "panel":
                if (!requireRoom(target, RELAY)) return;
                println("The relay panel hums behind a scratched service seam.");
                if (!panelOpened) {
                    println("A screwdriver would slip the casing open cleanly.");
                } else if (!powerRestored) {
                    println("Behind the casing sits a blown fuse slot. The panel cannot do much until that is fixed.");
                } else if (!relayCoilInstalled) {
                    println("Power is back, but the pod relay line still wants a coil. The observation systems should be awake now.");
                } else {
                    println("The relay line is live and feeding the pod network.");
                }
                break;

            case "grate":
                if (!requireRoom(target, FILTRATION)) return;
                if (!grateOpened) {
                    println(text.get("EXAMINE_GRATE_1"));
                    println(text.get("EXAMINE_GRATE_2"));
                } else if (!ventRouteOpen) {
                    println(text.get("EXAMINE_GRATE_3"));
                } else {
                    println(text.get("EXAMINE_GRATE_4"));
                    println("If you commit to the shaft from here, the route choice will matter.");
                }
                break;

            case "toolbox":
                if (!requireRoom(target, MAINTENANCE)) return;
                if (!toolboxOpened) {
                    requestChoice(
                        PendingRequestType.TOOLBOX_CHOICE,
                        "You force the toolbox open. The tray has a false bottom and no time to spare.",
                        "A) Grab the obvious tools fast",
                        "B) Search carefully for hidden supplies"
                    );
                } else if (rushedToolbox) {
                    println("The toolbox is empty except for the false bottom you ignored.");
                    println("If you rushed this bay, the emergency filter shelf nearby may still hide what you missed.");
                } else {
                    println("The toolbox is empty now. You already took the useful parts.");
                }
                break;

            case "safe":
                if (!requireRoom(target, SECURITY)) return;
                if (!safeOpened) {
                    println("The safe wants a 3-digit code. Nearby markings keep hinting at filter-bank numbers.");
                    requestCode(PendingRequestType.SAFE_CODE, "Enter 3-digit code:");
                } else {
                    println(text.get("SAFE_5"));
                }
                break;

            case "mask":
                if (!requireRoom(target, AIRLOCK)) return;
                if (!player.hasItem("oxygen mask")) {
                    requestChoice(
                        PendingRequestType.MASK_CHOICE,
                        text.get("MASK_1"),
                        "A) Put it on now",
                        "B) Take it, but save it for later"
                    );
                } else {
                    println(text.get("MASK_2"));
                }
                break;

            case "monitor":
                if (!requireRoom(target, OBSERVATION)) return;
                println("The monitor jitters between station warnings, route status, and diagnostics.");
                println("\"Emergency routes: MAIN / VENT / POD\"");
                println("\"Observation reflection shows hidden maintenance markings.\"");
                println("\"Security alerts continue to reference filter bank 2-4-7.\"");
                if (!powerRestored) {
                    println("A dark strip along the bottom suggests the unit is still starved for power.");
                } else if (!relayCoilVisible && !player.hasItem("relay coil") && !relayCoilInstalled) {
                    println("With auxiliary power restored, a side drawer clicks open beneath the screen.");
                    println("Inside is a compact relay coil sized for pod circuitry.");
                    relayCoilVisible = true;
                    player.addScore(8);
                } else if (relayCoilInstalled || player.hasItem("relay coil")) {
                    println("The observation drawer is already empty.");
                }
                break;

            case "locker":
                if (!requireRoom(target, SECURITY)) return;
                if (!player.hasItem("access card")) {
                    println("The locker is sealed behind higher access. The safe beside it looks like the more realistic way in.");
                } else if (!lockerOpened) {
                    println(text.get("LOCKER_2"));
                    println(text.get("LOCKER_3"));
                    player.addItem(new Item("battery"));
                    player.addItem(new Item("launch key"));
                    lockerOpened = true;
                    player.addScore(15);
                } else {
                    println(text.get("LOCKER_4"));
                }
                break;

            case "pod":
                if (!requireRoom(target, POD_BAY)) return;
                println(text.get("POD_1"));
                if (!podRouteUnlocked) {
                    println("The pod bus is still offline. The relay panel has to come back first.");
                } else if (!relayCoilInstalled) {
                    println("The pod relay cradle is still empty. Something from observation diagnostics should fit it.");
                } else if (!podPowered) {
                    println("The relay sync is live, but the pod still needs a battery and launch key.");
                } else {
                    println(text.get("POD_4"));
                }
                break;

            case "main door":
                if (!requireRoom(target, COMMAND)) return;
                println("The inner station door is thick enough to feel final.");
                if (!innerDoorUnlocked) {
                    println("Its control lock is still tied to the relay override.");
                } else if (!player.hasItem("main door code")) {
                    println("The lock has released, but the final four-digit departure code is still missing.");
                } else {
                    println("The main route is physically ready from here. Solve the final lock and choose whether to save the logs.");
                }
                break;

            case "logs":
                if (!requireRoom(target, COMMAND)) return;
                if (!powerRestored) {
                    println("The log terminal is awake enough to blink, but not enough to archive anything.");
                } else {
                    println("The terminal offers one last option: save the station logs before you go.");
                    println("That choice will cost time when the main route is ready.");
                }
                break;

            case "route map":
                if (!requireRoom(target, JUNCTION)) return;
                println("The route map shows a 3x3 service block.");
                println("Upper rooms are cleaner but poorer in supplies. The lower rooms are thicker with gas and hide the better tools.");
                println("Maintenance sits south-west, filtration due south, security south-east, relay to the east, command above, pod bay above the relay.");
                break;

            case "filter shelf":
                if (!requireRoom(target, FILTRATION)) return;
                println("The emergency shelf is stamped FILTER BANK 2 / 4 / 7.");
                if (rushedToolbox && !player.hasItem("fuse") && !spareFuseVisible) {
                    println("Behind a jammed cartridge, you spot the replacement fuse you rushed past earlier.");
                    spareFuseVisible = true;
                }
                if (!player.hasItem("filter patch") && !filterPatchVisible) {
                    println("A sealed patch kit hangs behind the same bracket.");
                    filterPatchVisible = true;
                }
                if (!spareFuseVisible && !filterPatchVisible && player.hasItem("fuse") && player.hasItem("filter patch")) {
                    println("The shelf has already given up anything worth taking.");
                }
                break;

            case "relay coil":
                if (!requireRoom(target, OBSERVATION)) return;
                if (!relayCoilVisible || relayCoilInstalled) {
                    println("There is no relay coil exposed here now.");
                } else {
                    println("You pocket the relay coil from the observation drawer.");
                    player.addItem(new Item("relay coil"));
                    relayCoilVisible = false;
                }
                break;

            case "filter patch":
                if (!requireRoom(target, FILTRATION)) return;
                if (!filterPatchVisible) {
                    println("No patch kit is exposed here right now.");
                } else {
                    println("You pull the filter patch free. It should reinforce the oxygen mask seal.");
                    player.addItem(new Item("filter patch"));
                    filterPatchVisible = false;
                }
                break;

            case "spare fuse":
                if (!requireRoom(target, FILTRATION)) return;
                if (!spareFuseVisible) {
                    println("There is no spare fuse visible here.");
                } else {
                    println("You take the spare fuse from the shelf.");
                    player.addItem(new Item("fuse"));
                    spareFuseVisible = false;
                }
                break;

            default:
                String roomHint = roomHintForObject(target);
                if (!roomHint.isEmpty()) {
                    println("That is not in this room. If it still matters, check " + roomHint + ".");
                } else {
                    println(text.get("EXAMINE_DEFAULT_1"));
                }
                break;
        }
    }

    private void examineInventoryItem(String itemName) {
        switch (itemName) {
            case "note":
                println("The note is a maintenance handoff sheet:");
                println("\"Observation reflection catches the hidden relay override.\"");
                println("\"Security keeps flagging filter bank 2-4-7.\"");
                if (powerRestored && !relayCoilInstalled && !player.hasItem("relay coil")) {
                    println("\"Once auxiliary power is stable, check the observation drawer.\"");
                }
                break;
            case "mirror":
                println("The mirror is small, polished, and most useful around tight machinery where markings hide at bad angles.");
                break;
            case "access card":
                println("The card is marked SECURITY ANNEX. It should satisfy the sealed locker there.");
                break;
            case "main door code":
                println("The strip still reads: 5193.");
                break;
            case "override code":
                println("The reflected override digits are 3917.");
                break;
            case "relay coil":
                println("The relay coil is shaped for the pod bus in the upper bay.");
                break;
            case "filter patch":
                println("A compressed seal patch. Use it on the oxygen mask to buy yourself more reliable air.");
                break;
            case "oxygen mask":
                println(filterPatchUsed
                    ? "The mask seal is reinforced and ready for the nastier rooms."
                    : "The mask is battered but functional. With a patch, it could seal better.");
                break;
            default:
                println("You give it a quick inspection, but there is nothing new to learn from it.");
                break;
        }
    }

    private void useItem(String rawTarget) {
        String itemName = parser.normalizeItem(rawTarget);

        if (!player.hasItem(itemName)) {
            println("You don't have that item. Inventory and room clues should tell you what is still missing.");
            return;
        }

        switch (itemName) {
            case "oxygen mask":
                if (!player.isMaskEquipped()) {
                    player.setMaskEquipped(true);
                    println(text.get("USE_MASK_1"));
                    println(text.get("USE_MASK_2"));
                    if (filterPatchUsed) {
                        println("The reinforced seal tightens over your face.");
                    }
                } else {
                    println(text.get("USE_MASK_3"));
                }
                break;

            case "mirror":
                if (!isCurrentRoom(RELAY)) {
                    println("The mirror matters most around the relay panel where markings can hide in the metal.");
                } else if (!panelOpened) {
                    println("The casing is too closed to reveal anything useful. Open the panel first.");
                } else if (!player.hasItem("override code")) {
                    println(text.get("USE_MIRROR_1"));
                    println(text.get("USE_MIRROR_2"));
                    player.addItem(new Item("override code"));
                    player.addScore(10);
                } else {
                    println(text.get("USE_MIRROR_3"));
                }
                break;

            case "note":
                examineInventoryItem("note");
                break;

            case "screwdriver":
                if (!isCurrentRoom(RELAY)) {
                    println("The screwdriver would be most useful on the relay panel in Systems Relay.");
                } else if (!panelOpened) {
                    panelOpened = true;
                    println(text.get("USE_SCREWDRIVER_1"));
                    println(text.get("USE_SCREWDRIVER_2"));
                    println("A second socket sits empty beside the fuse mount. The pod bus is missing a relay coil.");
                    player.addScore(10);
                } else {
                    println(text.get("USE_SCREWDRIVER_3"));
                }
                break;

            case "fuse":
                if (!isCurrentRoom(RELAY)) {
                    println("The fuse belongs in the exposed relay panel, not out here.");
                } else if (!panelOpened) {
                    println(text.get("USE_FUSE_1"));
                } else if (!powerRestored) {
                    powerRestored = true;
                    podRouteUnlocked = true;
                    player.removeItem("fuse");
                    println(text.get("USE_FUSE_2"));
                    println(text.get("USE_FUSE_3"));
                    println("Observation systems and security readers wake up across the grid.");
                    player.addScore(20);
                } else {
                    println(text.get("USE_FUSE_4"));
                }
                break;

            case "crowbar":
                if (!isCurrentRoom(FILTRATION)) {
                    println("Nothing here resists you like the bent vent grate in Filtration Access.");
                } else if (!grateOpened) {
                    grateOpened = true;
                    println(text.get("USE_CROWBAR_1"));
                    println(text.get("USE_CROWBAR_2"));
                    player.addScore(10);
                } else if (!ventRouteOpen) {
                    ventRouteOpen = true;
                    println(text.get("USE_CROWBAR_3"));
                    println(text.get("USE_CROWBAR_4"));
                    player.addScore(15);
                } else {
                    println(text.get("USE_CROWBAR_5"));
                }
                break;

            case "battery":
                if (!isCurrentRoom(POD_BAY)) {
                    println("The battery belongs in the pod console in the upper bay.");
                } else if (!podRouteUnlocked) {
                    println("The pod bus is still cold. Restore auxiliary power first.");
                } else if (!relayCoilInstalled) {
                    println("The pod still needs its relay coil before the battery can do anything.");
                } else if (!podPowered) {
                    podPowered = true;
                    player.removeItem("battery");
                    println(text.get("USE_BATTERY_2"));
                    println(text.get("USE_BATTERY_3"));
                    player.addScore(20);
                } else {
                    println(text.get("USE_BATTERY_4"));
                }
                break;

            case "access card":
                if (isCurrentRoom(SECURITY) && !lockerOpened) {
                    println("You swipe the access card at the locker reader.");
                    println(text.get("LOCKER_2"));
                    println(text.get("LOCKER_3"));
                    player.addItem(new Item("battery"));
                    player.addItem(new Item("launch key"));
                    lockerOpened = true;
                    player.addScore(15);
                } else {
                    println(text.get("USE_CARD_1"));
                }
                break;

            case "main door code":
                println(text.get("USE_CODE_1"));
                println("The number only matters from the Command Lock.");
                break;

            case "filter patch":
                if (!player.hasItem("oxygen mask")) {
                    println("The patch is only useful if you have the oxygen mask to reinforce.");
                } else if (filterPatchUsed) {
                    println("The mask seal is already reinforced.");
                } else {
                    filterPatchUsed = true;
                    player.removeItem("filter patch");
                    println("You reinforce the oxygen mask seal with the filter patch.");
                    println("It will hold back the lower-room gas for longer now.");
                    player.addScore(6);
                }
                break;

            case "relay coil":
                if (!isCurrentRoom(POD_BAY)) {
                    println("The relay coil belongs in the pod cradle upstairs.");
                } else if (!podRouteUnlocked) {
                    println("The pod bus still has no auxiliary power. The relay panel must come back first.");
                } else if (!relayCoilInstalled) {
                    relayCoilInstalled = true;
                    player.removeItem("relay coil");
                    println("You lock the relay coil into the pod bus socket.");
                    println("The console stabilizes and starts accepting live power.");
                    player.addScore(15);
                } else {
                    println("The pod relay socket is already filled.");
                }
                break;

            default:
                println(text.get("USE_DEFAULT_1"));
                break;
        }
    }

    private void solve(String rawTarget) {
        String target = rawTarget.toLowerCase().trim();

        if (target.contains("panel") || target.contains("override")) {
            if (!isCurrentRoom(RELAY)) {
                println("The relay override is in Systems Relay.");
                return;
            }
            if (!powerRestored) {
                println(text.get("SOLVE_PANEL_1"));
                return;
            }
            if (!player.hasItem("override code")) {
                println(text.get("SOLVE_PANEL_2"));
                return;
            }

            requestCode(PendingRequestType.OVERRIDE_CODE, "Enter the 4-digit override code:");
        } else if (target.contains("main")) {
            if (!isCurrentRoom(COMMAND)) {
                println("The final main lock is in the Command Lock.");
                return;
            }
            if (!innerDoorUnlocked) {
                println(text.get("SOLVE_MAIN_1"));
                return;
            }
            if (!player.hasItem("main door code")) {
                println(text.get("SOLVE_MAIN_2"));
                return;
            }

            if (!finalChoiceMade) {
                requestChoice(
                    PendingRequestType.FINAL_CHOICE,
                    "A flashing prompt appears beside the main door controls.",
                    "A) Save the station logs before leaving",
                    "B) Leave immediately and forget the logs"
                );
                return;
            }

            requestCode(PendingRequestType.MAIN_CODE, "Enter the main door code:");
        } else {
            println("You don't know how to solve that. The relay override and the main lock are the only coded systems here.");
        }
    }

    private void escape(String rawTarget) {
        String target = rawTarget.toLowerCase().trim();

        if (target.contains("main") || target.contains("door")) {
            if (!isCurrentRoom(COMMAND)) {
                println("The main route is physically in the Command Lock.");
            } else if (innerDoorUnlocked && player.hasItem("main door code")) {
                println(text.get("ESCAPE_MAIN_1"));
                println("Try: solve main");
            } else {
                println(text.get("ESCAPE_MAIN_2"));
            }
        } else if (target.contains("vent")) {
            if (!isCurrentRoom(FILTRATION)) {
                println("The vent route starts from Filtration Access.");
            } else if (!grateOpened) {
                println(text.get("ESCAPE_VENT_1"));
            } else if (!ventRouteOpen) {
                println(text.get("ESCAPE_VENT_2"));
            } else if (!ventChoiceMade) {
                requestChoice(
                    PendingRequestType.VENT_CHOICE,
                    "The vent shaft splits ahead.",
                    "A) Take the narrow upper route",
                    "B) Drop into the wider lower route"
                );
            } else {
                println(text.get("ESCAPE_VENT_3"));
                escapeSuccess("VENT SHAFT");
            }
        } else if (target.contains("pod")) {
            if (!isCurrentRoom(POD_BAY)) {
                println("The pod route begins in the Escape Pod Bay.");
            } else if (!podRouteUnlocked) {
                println("The escape pod still isn't tied into live power.");
            } else if (!relayCoilInstalled) {
                println("The pod bay is waiting on its relay coil.");
            } else if (!podPowered) {
                println(text.get("ESCAPE_POD_1"));
            } else if (!player.hasItem("launch key")) {
                println(text.get("ESCAPE_POD_2"));
            } else if (!podChoiceMade) {
                requestChoice(
                    PendingRequestType.POD_CHOICE,
                    "The pod hums, waiting for a final decision.",
                    "A) Inspect the pod systems first",
                    "B) Launch immediately and trust it"
                );
            } else {
                println(text.get("ESCAPE_POD_3"));
                escapeSuccess("ESCAPE POD");
            }
        } else {
            println(text.get("ESCAPE_DEFAULT_1"));
        }
    }

    private void requestChoice(PendingRequestType requestType, String prompt, String optionA, String optionB) {
        pendingRequest = requestType;
        pendingChoice = new Choice(prompt, optionA, optionB);
        pendingPrompt = "Choose A or B.";

        println(prompt);
        println(optionA);
        println(optionB);
    }

    private void requestCode(PendingRequestType requestType, String prompt) {
        pendingRequest = requestType;
        pendingChoice = null;
        pendingPrompt = prompt;
        println(prompt);
    }

    private void clearPendingRequest() {
        pendingRequest = PendingRequestType.NONE;
        pendingChoice = null;
        pendingPrompt = "";
    }

    private String normalizeChoice(String input) {
        String choice = input == null ? "" : input.trim().toUpperCase();
        if (choice.equals("A") || choice.equals("B")) {
            return choice;
        }
        return null;
    }
    private void escapeSuccess(String route) {
        escaped = true;
        gameOver = true;
        escapeRoute = route;
        clearPendingRequest();
        player.addScore(50);

        long elapsed = (System.currentTimeMillis() - startTime) / 1000;

        println("");
        println("========================================");
        println(text.get("WIN_1"));
        println("Route: " + route);
        println("Time taken: " + elapsed + " seconds");
        println("Final Score: " + player.getScore());
        printBranchEnding();
        println(text.get("WIN_2"));
        println("========================================");
    }

    private void printBranchEnding() {
        if (savedStationLogs) {
            println("You did not leave the station's story behind with the gas.");
        }
        if (toolboxChoiceMade && rushedToolbox) {
            println("You still wonder whether saving seconds in Maintenance Bay cost you more elsewhere.");
        }
        if (maskChoiceMade && savedMaskForLater) {
            println("Waiting on the mask kept your hands free, but the lower deck nearly punished the gamble.");
        }
        if (ventChoiceMade && tookUpperVent) {
            println("Your arms still ache from dragging yourself through the upper shaft.");
        }
        if (podChoiceMade && inspectedPodSystems) {
            println("Caution kept the pod from becoming just another coffin.");
        }
    }

    private void showStatus() {
        println("Status:");
        println("- Current room: " + getCurrentRoom().getName());
        println("- Room hazard: " + getCurrentRoom().getHazard().getLabel());
        println("- Exits: " + exitsText());
        println("- Panel opened: " + panelOpened);
        println("- Power restored: " + powerRestored);
        println("- Main door unlocked: " + innerDoorUnlocked);
        println("- Vent route open: " + ventRouteOpen);
        println("- Pod relay installed: " + relayCoilInstalled);
        println("- Pod powered: " + podPowered);
        println("- Mask equipped: " + player.isMaskEquipped());
        println("- Mask reinforced: " + filterPatchUsed);
    }

    private void showMap() {
        println("Station Block Map:");
        printMapRow(0);
        printMapRow(1);
        printMapRow(2);
        println("Legend: [*] current room");
    }

    private void printMapRow(int y) {
        StringBuilder row = new StringBuilder();
        for (Room room : rooms.values()) {
            if (room.getY() != y) {
                continue;
            }
            if (row.length() > 0) {
                row.append("  ");
            }
            String marker = room.getId().equals(currentRoomId) ? "[*] " : "[ ] ";
            row.append(marker).append(room.getName());
        }
        println(row.toString());
    }

    private void showHelp() {
        println("Available commands:");
        println("  look");
        println("  go [north / south / east / west]");
        println("  map");
        println("  examine [object]");
        println("  use [item]");
        println("  solve [panel / main]");
        println("  escape [main / vent / pod]");
        println("  inventory");
        println("  status");
        println("  help");
        println("  quit");
        println("");
        println("Important objects only respond when you are in the correct room.");
    }
    private Room getCurrentRoom() { return rooms.get(currentRoomId); }
    private Room getNeighbor(Direction direction) {
        Room current = getCurrentRoom();
        int targetX = current.getX() + direction.getDx();
        int targetY = current.getY() + direction.getDy();

        for (Room room : rooms.values()) {
            if (room.getX() == targetX && room.getY() == targetY) {
                return room;
            }
        }
        return null;
    }

    private List<Direction> getAvailableDirections() {
        List<Direction> directions = new ArrayList<>();
        for (Direction direction : Direction.values()) {
            if (getNeighbor(direction) != null) {
                directions.add(direction);
            }
        }
        return directions;
    }
    private boolean isCurrentRoom(String roomId) { return currentRoomId.equals(roomId); }
    private boolean requireRoom(String target, String roomId) {
        if (isCurrentRoom(roomId)) {
            return true;
        }
        println("That is not in this room. Check " + rooms.get(roomId).getName() + " instead.");
        return false;
    }

    private void consumeTime(int seconds) {
        startTime -= seconds * 1000L;
    }

    private String getRoomSummary(Room room) {
        return room.getSummary();
    }

    private String getRoomDetail(Room room) {
        switch (room.getId()) {
            case AIRLOCK:
                if (!player.hasItem("oxygen mask")) {
                    return "A wall-mounted oxygen mask is still clipped beside the manual seal. East leads deeper into the block.";
                }
                return "The safest air in the section is still here, but it will not stay safe forever.";
            case OBSERVATION:
                if (!powerRestored) {
                    return "A status monitor jitters through static, and the polished trim around it looks reflective enough to matter.";
                }
                return relayCoilVisible
                    ? "The powered monitor has kicked open a small drawer beneath it."
                    : "The monitor glow is steadier now, and the room is mostly just a clue station once the drawer is empty.";
            case COMMAND:
                if (!innerDoorUnlocked) {
                    return "The main inner door is physically here, but the relay override still owns it.";
                }
                return "The exit stands one solve away. The log terminal beside it offers one final moral delay.";
            case JUNCTION:
                return "A damaged route map here is the clearest orientation tool in the section.";
            case RELAY:
                if (!panelOpened) {
                    return "Sparks hiss behind the control panel. Opening it should expose whatever is blocking auxiliary power.";
                }
                if (!powerRestored) {
                    return "The panel interior exposes a blown fuse slot and a pod relay line waiting to come back.";
                }
                return "Auxiliary power is back. The relay line is stable enough to support the rest of the section.";
            case POD_BAY:
                if (!relayCoilInstalled) {
                    return "The pod cradle is close, but the relay socket and battery bus still need work.";
                }
                if (!podPowered) {
                    return "The relay sync is online. One battery and a launch key would wake the pod.";
                }
                return "The pod is awake, humming, and one decision away from launch.";
            case MAINTENANCE:
                return "This lower bay is thicker with gas, but it is the fastest place to arm yourself.";
            case FILTRATION:
                if (!grateOpened) {
                    return "The bent grate and the 2 / 4 / 7 filter shelf both look worth dealing with.";
                }
                if (!ventRouteOpen) {
                    return "The cover is off, but the deeper metal still blocks a clean crawl through the shaft.";
                }
                return "The vent route is open from here. It is ugly, but viable.";
            case SECURITY:
                return "This is one of the worst rooms to linger in, but the safe and locker can decide entire escape routes.";
            default:
                return "";
        }
    }

    private String exitsText() {
        List<Direction> directions = getAvailableDirections();
        List<String> parts = new ArrayList<>();
        for (Direction direction : directions) {
            Room neighbor = getNeighbor(direction);
            parts.add(direction.getLabel() + " to " + neighbor.getName());
        }
        return String.join(", ", parts);
    }

    private List<SceneObject> getVisibleObjectsForCurrentRoom() {
        List<SceneObject> objects = new ArrayList<>();

        switch (currentRoomId) {
            case AIRLOCK:
                if (!player.hasItem("oxygen mask")) {
                    objects.add(new SceneObject("mask", "oxygen mask", "examine oxygen mask", "item", true));
                }
                break;
            case OBSERVATION:
                objects.add(new SceneObject("monitor", "status monitor", "examine status monitor", "system", true));
                if (relayCoilVisible && !player.hasItem("relay coil") && !relayCoilInstalled) {
                    objects.add(new SceneObject("relay-coil", "relay coil", "examine relay coil", "item", true));
                }
                break;
            case COMMAND:
                objects.add(new SceneObject("main-door", "main door controls", "examine main door", "exit", true));
                objects.add(new SceneObject("logs", "station log terminal", "examine logs", "clue", true));
                break;
            case JUNCTION:
                objects.add(new SceneObject("route-map", "route map", "examine route map", "clue", true));
                break;
            case RELAY:
                objects.add(new SceneObject("panel", "control panel", "examine control panel", "system", true));
                if (panelOpened) {
                    objects.add(new SceneObject("panel-interior", powerRestored ? "powered panel internals" : "open panel internals", "", "state", false));
                }
                break;
            case POD_BAY:
                objects.add(new SceneObject("pod", "escape pod hatch", "examine escape pod hatch", "exit", true));
                if (podRouteUnlocked && !relayCoilInstalled) {
                    objects.add(new SceneObject("pod-socket", "relay socket", "", "state", false));
                }
                break;
            case MAINTENANCE:
                objects.add(new SceneObject("toolbox", "maintenance toolbox", "examine maintenance toolbox", "tool", true));
                break;
            case FILTRATION:
                objects.add(new SceneObject("grate", "ventilation grate", "examine ventilation grate", "exit", true));
                objects.add(new SceneObject("filter-shelf", "emergency filter shelf", "examine filter shelf", "clue", true));
                if (spareFuseVisible && !player.hasItem("fuse")) {
                    objects.add(new SceneObject("spare-fuse", "spare fuse", "examine spare fuse", "item", true));
                }
                if (filterPatchVisible && !player.hasItem("filter patch")) {
                    objects.add(new SceneObject("filter-patch", "filter patch", "examine filter patch", "item", true));
                }
                break;
            case SECURITY:
                objects.add(new SceneObject("safe", "wall safe", "examine wall safe", "system", true));
                objects.add(new SceneObject("locker", "storage locker", "examine storage locker", "system", true));
                break;
            default:
                break;
        }

        return objects;
    }
    private String directionsToJson() {
        List<String> values = new ArrayList<>();
        for (Direction direction : getAvailableDirections()) {
            values.add(direction.getLabel());
        }
        return toJsonArray(values);
    }

    private String roomsToJson() {
        StringBuilder json = new StringBuilder();
        json.append("[");
        int index = 0;

        for (Room room : rooms.values()) {
            if (index++ > 0) {
                json.append(",");
            }
            json.append("{");
            json.append("\"id\":\"").append(escapeJson(room.getId())).append("\",");
            json.append("\"name\":\"").append(escapeJson(room.getName())).append("\",");
            json.append("\"x\":").append(room.getX()).append(",");
            json.append("\"y\":").append(room.getY()).append(",");
            json.append("\"hazard\":\"").append(escapeJson(room.getHazard().getLabel())).append("\",");
            json.append("\"current\":").append(room.getId().equals(currentRoomId)).append(",");
            json.append("\"visited\":").append(visitedRooms.contains(room.getId()));
            json.append("}");
        }

        json.append("]");
        return json.toString();
    }

    private String sceneObjectsToJson(List<SceneObject> objects) {
        StringBuilder json = new StringBuilder();
        json.append("[");

        for (int i = 0; i < objects.size(); i++) {
            if (i > 0) {
                json.append(",");
            }
            json.append(objects.get(i).toJson(this));
        }

        json.append("]");
        return json.toString();
    }

    private String roomHintForObject(String target) {
        switch (target) {
            case "mask": return rooms.get(AIRLOCK).getName();
            case "monitor":
            case "relay coil": return rooms.get(OBSERVATION).getName();
            case "main door":
            case "logs": return rooms.get(COMMAND).getName();
            case "route map": return rooms.get(JUNCTION).getName();
            case "panel": return rooms.get(RELAY).getName();
            case "toolbox": return rooms.get(MAINTENANCE).getName();
            case "grate":
            case "filter shelf":
            case "spare fuse":
            case "filter patch": return rooms.get(FILTRATION).getName();
            case "safe":
            case "locker": return rooms.get(SECURITY).getName();
            case "pod": return rooms.get(POD_BAY).getName();
            default: return "";
        }
    }

    private String toChoiceJson() {
        if (pendingChoice == null) {
            return "null";
        }

        return "{"
            + "\"prompt\":\"" + escapeJson(pendingChoice.getPrompt()) + "\","
            + "\"optionA\":\"" + escapeJson(pendingChoice.getOptionA()) + "\","
            + "\"optionB\":\"" + escapeJson(pendingChoice.getOptionB()) + "\""
            + "}";
    }

    private String toJsonArray(List<String> values) {
        StringBuilder json = new StringBuilder();
        json.append("[");

        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                json.append(",");
            }
            json.append("\"").append(escapeJson(values.get(i))).append("\"");
        }

        json.append("]");
        return json.toString();
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }

        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\r", "")
            .replace("\n", "\\n");
    }

    private void println(String textLine) {
        String safeLine = textLine == null ? "" : textLine;
        logLines.add(safeLine);

        if (consoleMode) {
            System.out.println(safeLine);
        }
    }

    private static class SceneObject {
        private final String id;
        private final String label;
        private final String command;
        private final String type;
        private final boolean interactive;

        private SceneObject(String id, String label, String command, String type, boolean interactive) {
            this.id = id;
            this.label = label;
            this.command = command;
            this.type = type;
            this.interactive = interactive;
        }

        private String toJson(Game game) {
            return "{"
                + "\"id\":\"" + game.escapeJson(id) + "\","
                + "\"label\":\"" + game.escapeJson(label) + "\","
                + "\"command\":\"" + game.escapeJson(command) + "\","
                + "\"type\":\"" + game.escapeJson(type) + "\","
                + "\"interactive\":" + interactive
                + "}";
        }
    }
}
