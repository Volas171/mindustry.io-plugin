package mindustry.plugin.commands;

import arc.Core;
import arc.math.Mathf;
import arc.util.CommandHandler;
import mindustry.content.Bullets;
import mindustry.content.UnitTypes;
import mindustry.entities.bullet.BulletType;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.gen.Unit;
import mindustry.net.Administration;
import mindustry.plugin.datas.PlayerData;
import mindustry.plugin.discord.Context;
import mindustry.plugin.ioMain;
import mindustry.type.UnitType;
import net.dv8tion.jda.api.EmbedBuilder;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.HashMap;
import java.util.stream.IntStream;

import static mindustry.Vars.*;
import static mindustry.net.Administration.*;
import static mindustry.net.Packets.*;
import static mindustry.plugin.discord.Loader.serverName;
import static mindustry.plugin.ioMain.*;
import static mindustry.plugin.utils.Funcs.*;

public class ModeratorCommands {
    public ModeratorCommands(){
    }

    public void registerCommands(CommandHandler handler){
        handler.<Context>register("announce", "<text...>", "Display a message on top of the screen for all players for 10 seconds", (args, ctx) -> {
            Call.infoToast(args[0], 10);
            ctx.sendEmbed(true, ":round_pushpin: announcement sent successfully!", args[0]);
        });

        handler.<Context>register("event", "<ip> <port> [force?]", "Set the new event's ip & port. If force is set to true, all players will be forced to join immedietly.", (args, ctx) -> {
            eventIp = args[0];
            eventPort = Integer.parseInt(args[1]);
            boolean f = false;
            if(args.length >= 3 && Boolean.parseBoolean(args[2])) {
                f = true;
                Groups.player.forEach(player -> {
                    Call.connect(player.con, eventIp, eventPort);
                });
            }
            ctx.sendEmbed(true, ":crossed_swords: event ip set successfully!", args[0] + ":" + eventPort + (f ? "\nalso forced everyone to join" : ""));
        });

        handler.<Context>register("alert", "<player> <text...>", " " + serverName, (args, ctx) -> {
            Player player = findPlayer(args[0]);
            if(args[0].toLowerCase().equals("all")){
                Call.infoMessage(args[1]);
                ctx.sendEmbed(true, ":round_pushpin: alert to everyone sent successfully!", args[1]);
            }else{
                if(player != null){
                    Call.infoMessage(player.con, args[1]);
                    ctx.sendEmbed(true, ":round_pushpin: alert to " + escapeCharacters(player.name) + " sent successfully!", args[1]);
                }else{
                    ctx.sendEmbed(false, ":round_pushpin: can't find player " + args[1]);
                }
            }
        });

        handler.<Context>register("ban", "<player> <minutes> [reason...]", "Ban a player by the provided name, id or uuid (do offline bans using uuid)", (args, ctx) -> {
            Player player = findPlayer(args[0]);
            if(player != null){
                PlayerData pd = playerDataHashMap.get(player.uuid());
                if(pd != null){
                    long until = Instant.now().getEpochSecond() + Integer.parseInt(args[1]) * 60;
                    pd.bannedUntil = until;
                    pd.banReason = (args.length >= 3 ? args[2] : "not specified") + "\n" + "[accent]Until: " + epochToString(until) + "\n[accent]Ban ID:[] " + player.uuid().substring(0, 4);
                    playerDataHashMap.put(player.uuid(), pd);
                    // setJedisData(player.uuid, pd);
                    HashMap<String, String> fields = new HashMap<>();
                    fields.put("UUID", player.uuid());
                    ctx.sendEmbed(true, ":hammer: the ban hammer has been swung at " + escapeCharacters(player.name), "reason: *" + escapeColorCodes(pd.banReason) + "*", fields, false);
                    player.con.kick(KickReason.banned);
                }else{
                    ctx.sendEmbed(false, ":interrobang: internal server error, please ping fuzz");
                }
            }else{
                PlayerData pd = getJedisData(args[0]);
                if(pd != null){
                    long until = Instant.now().getEpochSecond() + Integer.parseInt(args[1]) * 60;
                    pd.bannedUntil = until;
                    pd.banReason = (args.length >= 3 ? args[2] : "not specified") + "\n" + "[accent]Until: " + epochToString(until) + "\n[accent]Ban ID:[] " + args[0].substring(0, 4);
                    setJedisData(args[0], pd);
                    HashMap<String, String> fields = new HashMap<>();
                    fields.put("UUID", args[0]);
                    ctx.sendEmbed(true, ":hammer: the ban hammer has been swung at " + escapeCharacters(netServer.admins.getInfo(args[0]).lastName),"reason: *" + escapeColorCodes(pd.banReason) + "*", fields, false);
                }else{
                    ctx.sendEmbed(false, ":hammer: that player or uuid cannot be found");
                }
            }
        });

        handler.<Context>register("kick", "<player>", "Kick a player from " + serverName, (args, ctx) -> {
            Player player = findPlayer(args[0]);
            if(player != null){
                player.con.kick(KickReason.kick);
                ctx.sendEmbed(true, ":football: kicked " + escapeCharacters(player.name) + " successfully!", player.uuid());
            }else{
                ctx.sendEmbed(false, ":round_pushpin: can't find player " + escapeCharacters(args[0]));
            }
        });

        handler.<Context>register("unban", "<uuid>", "Unban the specified player by uuid (works for votekicks as well)", (args, ctx) -> {
            PlayerData pd = getJedisData(args[0]);
            if(pd!= null){
                PlayerInfo info = netServer.admins.getInfo(args[0]);
                info.lastKicked = 0;
                pd.bannedUntil = 0;
                setJedisData(args[0], pd);
                ctx.sendEmbed(true, ":wrench: unbanned " + escapeCharacters(info.lastName) + " successfully!");
            }else{
                ctx.sendEmbed(false, ":wrench: that uuid doesn't exist in the database..");
            }
        });

        handler.<Context>register("playersinfo", "Check the information about all players on the server.", (args, ctx) -> {
            EmbedBuilder eb = new EmbedBuilder();
            eb.setColor(Pals.progress);
            eb.setTitle(":satellite: **players online: **" + Groups.player.size());

            StringBuilder pi = new StringBuilder();
            int pn = 1;
            for(Player p : Groups.player){
                if (!p.admin) {
                    pi
                            .append("**")
                            .append(pn + "•")
                            .append("** `")
                            .append(escapeCharacters(p.name))
                            .append("` : ")
                            .append(p.con.address)
                            .append(" : ")
                            .append(p.uuid())
                            .append("\n");
                } else {
                    pi
                            .append("**")
                            .append(pn + "•")
                            .append("** `")
                            .append(escapeCharacters(p.name))
                            .append("`")
                            .append("\n");


                }

                pn++;
            }
            eb.setDescription(pi);
            ctx.sendEmbed(eb);

        });

        handler.<Context>register("lookup", "<uuid|name>", "Lookup the specified player by uuid or name (name search only works when player is online)", (args, ctx) -> {
            EmbedBuilder eb = new EmbedBuilder();
            Administration.PlayerInfo info;
            Player player = findPlayer(args[0]);
            if (player != null) {
                info = netServer.admins.getInfo(player.uuid());
            } else{
                if(args[0].length() == 24) { // uuid length
                    info = netServer.admins.getInfo(args[0]);
                }else{
                    ctx.sendEmbed(false, ":mag: can't find that uuid in the database..");
                    return;
                }
            }
            eb.setColor(Pals.progress);
            eb.setTitle(":mag: " + escapeCharacters(info.lastName) + "'s lookup");
            eb.addField("UUID", info.id, false);
            eb.addField("Last used ip", info.lastIP, true);
            eb.addField("Times kicked", String.valueOf(info.timesKicked), true);



            StringBuilder s = new StringBuilder();
            s.append("**All used names: **\n");
            for (String name : info.names) {
                s.append(escapeCharacters(name)).append(" / ");
            }
            s.append("\n\n**All used IPs: **\n");
            for (String ip : info.ips) {
                s.append(escapeCharacters(ip)).append(" / ");
            }
            eb.setDescription(s.toString());
            ctx.channel.sendMessage(eb.build()).queue();
        });

        handler.<Context>register("setrank", "<uuid> <rank>", "Set the specified uuid's rank to the one provided.", (args, ctx) -> {
            int rank;
            try{
                rank = Integer.parseInt(args[1]);
            }catch (NumberFormatException e) {
                ctx.sendEmbed(false, ":wrench: error parsing rank number");
                return;
            }
            if(rank < rankNames.size()) {
                PlayerData pd = playerDataHashMap.containsKey(args[0]) ? playerDataHashMap.get(args[0]) : getJedisData(args[0]);
                if (pd != null) {
                    pd.rank = rank;
                    if(playerDataHashMap.containsKey(args[0])){
                        playerDataHashMap.put(args[0],  pd);
                    }
                    setJedisData(args[0], pd);
                    PlayerInfo info = netServer.admins.getInfo(args[0]);
                    ctx.sendEmbed(true, ":wrench: set " + escapeCharacters(info.lastName) + "'s rank to " + escapeColorCodes(rankNames.get(rank).name));
                } else {
                    ctx.sendEmbed(false, ":wrench: that uuid doesn't exist in the database..");
                }
            }else{
                ctx.sendEmbed(false, ":wrench: error parsing rank number");
            }
        });

        handler.<Context>register("convert", "<player> <unit>", "Change a players unit into the specified one", (args, ctx) -> {
            UnitType desiredUnit;
            try {
                Field field = UnitTypes.class.getDeclaredField(args[1]);
                desiredUnit = (UnitType)field.get(null);
            } catch (NoSuchFieldException | IllegalAccessException ignored) {
                ctx.sendEmbed(false, ":robot: that unit doesn't exist");
                return;
            }
            Player player = findPlayer(args[0]);
            if(player != null){

                Unit nu = desiredUnit.create(player.team());
                nu.set(player.getX(), player.getY());
                nu.add();

                player.unit().kill();
                player.unit(nu);
                player.afterSync();

                ctx.sendEmbed(true, ":robot: changed " + escapeCharacters(player.name) + "'s unit to " + desiredUnit.name);
            }else if(args[0].toLowerCase().equals("all")){
                for(Player p : Groups.player){
                    Unit nu = desiredUnit.create(p.team());
                    nu.set(p.getX(), p.getY());
                    nu.add();

                    p.unit().kill();
                    p.unit(nu);
                    p.afterSync();
                }
                ctx.sendEmbed(true, ":robot: changed everyone's unit to " + desiredUnit.name);
            }else{
                ctx.sendEmbed(false, ":robot: can't find " + escapeCharacters(args[0]));
            }
        });

        handler.<Context>register("team", "<player> <teamid>", "Change a players team into the specified team id", (args, ctx) -> {
            int teamid;
            try{
                teamid = Integer.parseInt(args[1]);
            }catch (Exception e){ ctx.sendEmbed(false, ":triangular_flag_on_post: error parsing team id number"); return;}

            Player player = findPlayer(args[0]);
            if(player != null){
                player.team(Team.get(teamid));
                ctx.sendEmbed(true, ":triangular_flag_on_post: changed " + escapeCharacters(player.name) + "'s team to " + Team.get(teamid).name);
            }else if(args[0].toLowerCase().equals("all")){
                for(Player p : Groups.player){ p.team(Team.get(teamid)); }
                ctx.sendEmbed(true, ":triangular_flag_on_post: changed everyone's team to " + Team.get(teamid).name);
            }else{
                ctx.sendEmbed(false, ":triangular_flag_on_post: can't find " + escapeCharacters(args[0]));
            }
        });

        handler.<Context>register("motd", "<message...>", "Change the welcome message popup when a new player joins Set to 'none' if you want to disable motd", (args, ctx) -> {
            if(args[0].toLowerCase().equals("none")){
                welcomeMessage = "";
                ctx.sendEmbed(true, ":newspaper: disabled welcome message successfully!");
            }else{
                welcomeMessage = args[0];
                ctx.sendEmbed(true, ":newspaper: changed welcome message successfully!", args[0]);
            }
            Core.settings.put("welcomeMessage", welcomeMessage);
            Core.settings.forceSave();
        });

        handler.<Context>register("statmessage", "<message...>", "Change the stat message popup when a player uses the /info command", (args, ctx) -> {
            if(args[0].toLowerCase().equals("none")){
                statMessage = "";
                ctx.sendEmbed(true, ":newspaper: disabled stat message successfully!");
            }else{
                statMessage = args[0];
                ctx.sendEmbed(true, ":newspaper: changed stat message successfully!", args[0]);
            }
            Core.settings.put("statMessage", statMessage);
            Core.settings.forceSave();
        });

        handler.<Context>register("spawn", "<player> <unit> <amount>", "Spawn a specified amount of units near the player's position.", (args, ctx) -> {
            int amt;
            try{
                amt = Integer.parseInt(args[2]);
            }catch (Exception e){ ctx.sendEmbed(false, ":robot: error parsing amount number"); return;}

            UnitType desiredUnitType = UnitTypes.dagger;
            try {
                Field field = UnitTypes.class.getDeclaredField(args[1]);
                desiredUnitType = (UnitType) field.get(null);
            } catch (NoSuchFieldException | IllegalAccessException ignored) {
                ctx.sendEmbed(false, ":robot: that unit doesn't exist");
                return;
            }
            Player player = findPlayer(args[0]);
            if(player != null){
                UnitType finalDesiredUnitType = desiredUnitType;
                IntStream.range(0, amt).forEach(i -> {
                    Unit unit = finalDesiredUnitType.create(player.team());
                    unit.set(player.getX(), player.getY());
                    unit.add();
                });
                ctx.sendEmbed(true, ":robot: spawned " + amt + " " + finalDesiredUnitType + "s at " + escapeColorCodes(player.name) + "'s position");
            }else{
                ctx.sendEmbed(false, ":robot: can't find " + escapeCharacters(args[0]));
            }
        });

        handler.<Context>register("kill", "<player|unit>", "Kill the specified player or all specified units on the map.", (args, ctx) -> {
            UnitType desiredUnitType;
            try {
                Field field = UnitTypes.class.getDeclaredField(args[0]);
                desiredUnitType = (UnitType) field.get(null);
                int amt = 0;
                for(Unit unit : Groups.unit){
                    if(unit.type == desiredUnitType){ unit.kill(); amt++; }
                }
                ctx.sendEmbed(true, ":knife: killed " + amt + " " + args[0] + "s");
            } catch (NoSuchFieldException | IllegalAccessException ignored) {
                Player player = findPlayer(args[0]);
                if(player != null){
                    player.unit().kill();
                    ctx.sendEmbed(true, ":knife: killed " + escapeCharacters(player.name));
                }else if(args[0].toLowerCase().equals("all")){
                    Groups.player.forEach(p -> p.unit().kill());
                    ctx.sendEmbed(true, ":knife: killed everyone, muhahaha");
                }else{
                    ctx.sendEmbed(false, ":knife: can't find " + escapeCharacters(args[0]));
                }
            }
        });

        handler.<Context>register("weapon", "<player> <bullet> [damage] [lifetime] [velocity]", "Modify the specified players weapon with the provided parameters", (args, ctx) -> {
            BulletType desiredBulletType;
            float dmg = 1f;
            float life = 1f;
            float vel = 1f;
            if(args.length > 2){
                try{
                    dmg = Float.parseFloat(args[2]);
                }catch (Exception e){ ctx.sendEmbed(false, ":gun: error parsing damage number"); return;}
            }
            if(args.length > 3){
                try{
                    life = Float.parseFloat(args[3]);
                }catch (Exception e){ ctx.sendEmbed(false, ":gun: error parsing lifetime number"); return;}
            }
            if(args.length > 4){
                try{
                    vel = Float.parseFloat(args[4]);
                }catch (Exception e){ ctx.sendEmbed(false, ":gun: error parsing velocity number"); return;}
            }
            try {
                Field field = Bullets.class.getDeclaredField(args[1]);
                desiredBulletType = (BulletType) field.get(null);
            } catch (NoSuchFieldException | IllegalAccessException ignored) {
                ctx.sendEmbed(false, ":gun: invalid bullet type");
                desiredBulletType = null;
            }
            HashMap<String, String> fields = new HashMap<>();
            Player player = findPlayer(args[0]);

            if(player != null){
                PlayerData pd = playerDataHashMap.get(player.uuid());
                pd.bt = desiredBulletType;
                pd.sclDamage = dmg;
                pd.sclLifetime = life;
                pd.sclVelocity = vel;
                playerDataHashMap.put(player.uuid(), pd);
                fields.put("Bullet", args[1]);
                fields.put("Bullet lifetime", args[2]);
                fields.put("Bullet velocity", args[3]);
                ctx.sendEmbed(true, ":gun: modded " + escapeCharacters(player.name) + "'s gun", fields, true);
            }else if(args[0].toLowerCase().equals("all")){
                for(Player p : Groups.player) {
                    PlayerData pd = playerDataHashMap.get(p.uuid());
                    pd.bt = desiredBulletType;
                    pd.sclDamage = dmg;
                    pd.sclLifetime = life;
                    pd.sclVelocity = vel;
                    playerDataHashMap.put(p.uuid(), pd);
                }
                fields.put("Bullet", args[1]);
                fields.put("Bullet lifetime", args[2]);
                fields.put("Bullet velocity", args[3]);
                ctx.sendEmbed(true, ":gun: modded everyone's gun", fields, true);
            }else{
                ctx.sendEmbed(false, ":gun: can't find " + escapeCharacters(args[0]));
            }
        });
    }
}