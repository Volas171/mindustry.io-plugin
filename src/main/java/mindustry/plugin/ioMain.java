package mindustry.plugin;

import java.time.Instant;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;

import arc.Core;
import arc.math.Mathf;
import arc.util.*;
import arc.util.Timer;
import mindustry.content.*;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.graphics.Pal;
import mindustry.mod.Plugin;
import mindustry.net.Administration;
import mindustry.plugin.datas.ContentHandler;
import mindustry.plugin.datas.PlayerData;
import mindustry.plugin.datas.TileInfo;
import mindustry.plugin.discord.Loader;
import mindustry.plugin.utils.Funcs;
import mindustry.plugin.utils.MapRules;
import mindustry.plugin.utils.VoteSession;
import mindustry.world.Tile;

import arc.Events;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.gen.Call;

import static mindustry.Vars.*;
import static mindustry.plugin.utils.Funcs.*;
import static mindustry.plugin.discord.Loader.*;

public class ioMain extends Plugin {
    public static HashMap<String, PlayerData> playerDataHashMap = new HashMap<>();
    public static int minutesPassed = 0;
    public static HashMap<Tile, TileInfo> tileInfoHashMap = new HashMap<>();
    //register event handlers and create variables in the constructor
    public ioMain() {
        //we can load this before anything else, it doesnt matter
        Loader.load();

        // display on screen messages
        float duration = 10f;
        int start = 450;
        int increment = 30;

        Timer.schedule(() -> {
            int currentInc = 0;
            for(String msg : onScreenMessages){
                Call.infoPopup(msg, duration, 20, 50, 20, start + currentInc, 0);
                currentInc = currentInc + increment;
            }
        }, 0, 10);

        Events.on(EventType.ServerLoadEvent.class, event -> {
            contentHandler = new ContentHandler();
            Log.info("Everything's loaded !");
        });

        Events.on(EventType.TapEvent.class, tapEvent -> {
            if(tapEvent.tile != null) {
                Player player = tapEvent.player;
                PlayerData pd = playerDataHashMap.get(player.uuid());

                Tile t = tapEvent.tile;
                pd.tapTile = t;
                if (pd.inspector) {
                    Call.effect(player.con, Fx.placeBlock, t.worldx(), t.worldy(), 0.75f, Pal.accent);
                    player.sendMessage("[orange]--[] [accent]tile [](" + t.x + ", " + t.y + ")[accent] block:[] " + ((t.block() == null || t.block() == Blocks.air) ? "[#545454]none" : t.block().name) + " [orange]--[]");
                    TileInfo info = tileInfoHashMap.getOrDefault(t, new TileInfo());
                    if (info.placedBy != null) {
                        String pBy = (player.admin() ? info.placedByUUID : info.placedBy);
                        player.sendMessage("[accent]last placed by:[] " + escapeColorCodes(pBy));
                    }
                    if (info.destroyedBy != null) {
                        String dBy = (player.admin() ? info.destroyedByUUID : info.destroyedBy);
                        player.sendMessage("[accent]last [scarlet]deconstructed[] by:[] " + escapeColorCodes(dBy));
                    }
                    if (t.block() == Blocks.air && info.wasHere != null){
                        player.sendMessage("[accent]block that was here:[] " + info.wasHere);
                    }
                    if (info.configuredBy != null) {
                        String cBy = (player.admin() ? info.configuredByUUID : info.configuredBy);
                        player.sendMessage("[accent]last configured by:[] " + escapeColorCodes(cBy));
                    }
                }
            }
        });

        // player disconnected
        Events.on(EventType.PlayerLeave.class, event -> {
            String uuid = event.player.uuid();
            if(playerDataHashMap.containsKey(uuid))
                setJedisData(uuid, playerDataHashMap.get(uuid));

            //free ram
            playerDataHashMap.remove(uuid);
        });

        // player joined
        Events.on(EventType.PlayerJoin.class, event -> {
            CompletableFuture.runAsync(() -> {
                Player player = event.player;
                PlayerData pd = getJedisData(player.uuid());
                if(pd == null && playerDataHashMap.containsKey(player.uuid())){
                    pd = playerDataHashMap.get(player.uuid());
                }

                if (pd != null) {
                    if (pd.bannedUntil > Instant.now().getEpochSecond()) {
                        player.con.kick("[scarlet]You are banned.[accent] Reason:\n" + pd.banReason);
                        return;
                    }
                    if(pd.rank > 0){
                        pd.tag = rankNames.get(pd.rank).tag;
                        // todo: figure out a non-intrusive way to do this
                        // player.name(pd.tag + " " + player.name);
                    }
                } else { // not in database
                    pd = new PlayerData(0);
                    setJedisData(player.uuid(), new PlayerData(0));
                }
                playerDataHashMap.put(player.uuid(), pd);

                if (welcomeMessage.length() > 0 && !welcomeMessage.equals("none")) {
                    Call.infoMessage(player.con, formatMessage(player, welcomeMessage));
                }

                if (pd != null){
                    if (pd.bannedUntil > Instant.now().getEpochSecond()){
                        for (int i=0; i < 30; i++)
                            Call.infoMessage(player.con, formatMessage(player, welcomeMessage));

                        player.con.kick("[scarlet]You are banned.[accent] Reason:\n" + pd.banReason, 0);
                    }
                }

                if(bannedNames.contains(player.name.trim().toLowerCase()))
                    player.con.kick("Influx Capacitor failed. Quantom leap needs to be restarted.");
            });
        });


        Events.on(EventType.BuildSelectEvent.class, event -> {
            if(event.builder instanceof Player){
                if(event.tile != null){
                    Player player = (Player) event.builder;
                    PlayerData pd = playerDataHashMap.get(player.uuid());

                    TileInfo info = tileInfoHashMap.getOrDefault(event.tile, new TileInfo());
                    if(!event.breaking){
                        info.placedBy = player.name;
                        info.placedByUUID = player.uuid();
                        info.wasHere = (event.tile.block() != Blocks.air ? event.tile.block().name : "[#545454]none");

                        pd.buildingsBuilt++;
                        playerDataHashMap.put(player.uuid(), pd);
                    } else{
                        info.destroyedBy = player.name;
                        info.destroyedByUUID = player.uuid();
                    }
                    tileInfoHashMap.put(event.tile, info);
                }
            }
        });

        Events.on(EventType.TapEvent.class, event -> {
            if(event.tile != null & event.player != null){
                TileInfo info = tileInfoHashMap.getOrDefault(event.tile, new TileInfo());
                Player player = event.player;
                info.configuredBy = player.name;
                info.configuredByUUID = player.uuid();
                tileInfoHashMap.put(event.tile, info);
            }
        });

        Events.on(EventType.WorldLoadEvent.class, event -> {
            Timer.schedule(MapRules::run, 1); // idk
        });

        Events.on(EventType.ServerLoadEvent.class, event -> {
            // action filter
            Vars.netServer.admins.addActionFilter(action -> {
                Player player = action.player;
                PlayerData pd = playerDataHashMap.get(player.uuid());

                if (player == null) return true;

                if (player.admin()) return true;
                if (!pd.canInteract) return false;

                return action.type != Administration.ActionType.rotate;
            });
        });

        Events.on(EventType.Trigger.update.getClass(), event -> {
            for(Player p : Groups.player){
                PlayerData pd = playerDataHashMap.get(p.uuid());
                if (pd != null && pd.bt != null && p.shooting()) {
                    Call.createBullet(pd.bt, p.team(), p.getX(), p.getY(), p.unit().rotation, pd.sclDamage, pd.sclVelocity, pd.sclLifetime);
                }
            }
        });

        Events.on(EventType.GameOverEvent.class, event -> {
            for(Player p : Groups.player){
                PlayerData pd = playerDataHashMap.get(p.uuid());
                pd.gamesPlayed++;
                playerDataHashMap.put(p.uuid(), pd);
            }
        });
    }

    //register commands that run on the server
    @Override
    public void registerServerCommands(CommandHandler handler){
        handler.removeCommand("exit");
        handler.register("exit", "exits the server", ioMain::exit);
    }

    //cooldown between map votes
    int voteCooldown = 120 * 1;

    //register commands that player can invoke in-game
    @Override
    public void registerClientCommands(CommandHandler handler){
        if (api != null) {

            handler.<Player>register("inspector", "Toggle on tile inspector. (Grief detection)", (args, player) -> {
                PlayerData pd = playerDataHashMap.get(player.uuid());
                pd.inspector = !pd.inspector;
                player.sendMessage("[accent]Tile inspector " + (pd.inspector ? "enabled" : "disabled") + ".");
            });

            handler.<Player>register("stats", "[player...]", "Display stats of the specified player (or yourself, if no player provided)", (args, player) -> {
                if(!statMessage.equals("none")) {
                    if (args.length <= 0) {
                        Call.infoMessage(player.con, formatMessage(player, statMessage));
                    } else {
                        Player p = findPlayer(args[0]);
                        if (p != null) {
                            Call.infoMessage(player.con, formatMessage(p, statMessage));
                        } else {
                            player.sendMessage("[lightgray]Can't find that player!");
                        }
                    }
                }
            });

            handler.<Player>register("event", "Join an ongoing event (if there is one)", (args, player) -> {
                if(eventIp.length() > 0){
                    Call.connect(player.con, eventIp, eventPort);
                } else{
                    player.sendMessage("[accent]There is no ongoing event at this time.");
                }
            });

            handler.<Player>register("maps","[page]", "Display all maps in the playlist.", (args, player) -> { // self info
                if(args.length > 0 && !Strings.canParseInt(args[0])){
                    player.sendMessage("[scarlet]'page' must be a number.");
                    return;
                }
                int perPage = 6;
                int page = args.length > 0 ? Strings.parseInt(args[0]) : 1;
                int pages = Mathf.ceil((float)Vars.maps.customMaps().size / perPage);

                page --;

                if(page >= pages || page < 0){
                    player.sendMessage("[scarlet]'page' must be a number between[orange] 1[] and[orange] " + pages + "[scarlet].");
                    return;
                }

                StringBuilder result = new StringBuilder();
                result.append(Strings.format("[orange]-- Maps Page[lightgray] {0}[gray]/[lightgray]{1}[orange] --\n\n", (page+1), pages));

                for(int i = perPage * page; i < Math.min(perPage * (page + 1), Vars.maps.customMaps().size); i++){
                    mindustry.maps.Map map = Vars.maps.customMaps().get(i);
                    result.append("[white] - [accent]").append(escapeColorCodes(map.name())).append("\n");
                }
                player.sendMessage(result.toString());
            });

            Timekeeper vtime = new Timekeeper(voteCooldown);
            VoteSession[] currentlyKicking = {null};

            handler.<Player>register("nominate","<map...>", "[regular+] Vote to change to a specific map.", (args, player) -> {
                if(!state.rules.pvp || player.admin()) {
                    mindustry.maps.Map found = getMapBySelector(args[0]);
                    if(found != null){
                        if(!vtime.get()){
                            player.sendMessage("[scarlet]You must wait " + voteCooldown/60 + " minutes between nominations.");
                            return;
                        }
                        VoteSession session = new VoteSession(currentlyKicking, found);

                        session.vote(player, 1);
                        vtime.reset();
                        currentlyKicking[0] = session;
                    }else{
                        player.sendMessage("[scarlet]No map[orange]'" + args[0] + "'[scarlet] found.");
                    }
                } else {
                    player.sendMessage("[scarlet]This command is disabled on pvp.");
                }
            });

            handler.<Player>register("rtv", "Vote to change the map.", (args, player) -> { // self info
                if(currentlyKicking[0] == null){
                    player.sendMessage("[scarlet]No map is being voted on.");
                }else{
                    //hosts can vote all they want
                    if(player.uuid() != null && (currentlyKicking[0].voted.contains(player.uuid()) || currentlyKicking[0].voted.contains(netServer.admins.getInfo(player.uuid()).lastIP))){
                        player.sendMessage("[scarlet]You've already voted.");
                        return;
                    }

                    currentlyKicking[0].vote(player, 1);
                }
            });
        }

    }

    public static void exit(String[] uselessness){
        exit();
    }

    public static void exit(){
        if(playerDataHashMap != null){
            playerDataHashMap.forEach(Funcs::setJedisData);
        }
        if(api != null){
            api.shutdownNow();
        }
        Vars.net.dispose();
        Core.app.exit();
        System.exit(0);
    }

}