package mindustry.plugin.commands;

import arc.files.Fi;
import arc.struct.Seq;
import arc.util.CommandHandler;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.io.SaveIO;
import mindustry.maps.Map;

import mindustry.gen.Call;
import mindustry.plugin.datas.ContentHandler;
import mindustry.plugin.discord.Context;
import net.dv8tion.jda.api.EmbedBuilder;

import javax.imageio.ImageIO;
import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.zip.InflaterInputStream;

import static arc.util.CommandHandler.*;
import static mindustry.Vars.*;
import static mindustry.plugin.utils.Funcs.*;
import static mindustry.plugin.discord.Loader.*;
import static net.dv8tion.jda.api.entities.Message.*;

public class PublicCommands {
    public void registerCommands(CommandHandler handler) {
        handler.<Context>register("chat", "<message...>", "Send a message to in-game chat in " + serverName, (args, ctx) -> {
            if(args[0].length() < chatMessageMaxSize){
                Call.sendMessage("[sky]" + ctx.author.getAsTag() + " @discord >[] " + args[0]);
                ctx.sendEmbed(true, ":mailbox_with_mail: **message sent!**", "``" + escapeCharacters(args[0]) + "``");
            } else{
                ctx.sendEmbed(false, ":exclamation: **message too big!**", "maximum size: **" + chatMessageMaxSize + " characters**");
            }
        });

        handler.<Context>register("maps", "Displays all available maps in the playlist. Use " + prefix + "map <name> to download a specific map.", (args, ctx) -> {
            Seq<Map> mapList = maps.customMaps();
            StringBuilder smallMaps = new StringBuilder();
            StringBuilder mediumMaps = new StringBuilder();
            StringBuilder bigMaps = new StringBuilder();

            for(Map map : mapList){
                int size = map.height * map.width;
                if(size <= 62500) { smallMaps.append("**").append(escapeCharacters(map.name())).append("** ").append(map.width).append("x").append(map.height).append("\n"); }
                if(size > 62500 && size < 160000) { mediumMaps.append("**").append(escapeCharacters(map.name())).append("** ").append(map.width).append("x").append(map.height).append("\n"); }
                if(size >= 160000) { bigMaps.append("**").append(escapeCharacters(map.name())).append("** ").append(map.width).append("x").append(map.height).append("\n"); }
            }
            HashMap<String, String> fields = new HashMap<>();
            if(smallMaps.length() > 0){fields.put("small maps", smallMaps.toString()); }
            if(mediumMaps.length() > 0){fields.put("medium maps", mediumMaps.toString()); }
            if(bigMaps.length() > 0){fields.put("big maps", bigMaps.toString()); }

            ctx.sendEmbed(true,":map: **" + mapList.size + " maps** in " + serverName + "'s playlist", fields, true);
        });

        handler.<Context>register("map","<map...>", "Previews and provides a download for the specified map. (check maps with " + prefix + "maps)", (args, ctx) -> {
            Map map = getMapBySelector(args[0].trim());
            if (map != null){
                try {
                    Fi mapFile = map.file;

                    ContentHandler.Map visualMap = contentHandler.readMap(map.file.read());
                    File imageFile = new File(assets + "image_" + mapFile.name().replaceAll(".msav", ".png"));
                    ImageIO.write(visualMap.image, "png", imageFile);


                    EmbedBuilder eb = new EmbedBuilder().setColor(Pals.success).setTitle(":map: " + escapeCharacters(map.name())).setFooter(map.width + "x" + map.height).setDescription(escapeCharacters(map.description())).setAuthor(escapeCharacters(map.author()));
                    eb.setImage("attachment://" + imageFile.getName());
                    ctx.channel.sendFile(mapFile.file()).addFile(imageFile).embed(eb.build()).queue();
                    //ctx.channel.sendFile(mapFile.file()).embed(eb.build()).queue();
                } catch (Exception e) {
                    ctx.sendEmbed(false, ":eyes: **internal server error**");
                    e.printStackTrace();
                }
            }else{
                ctx.sendEmbed(false, ":mag: map **" + escapeCharacters(args[0]) + "** not found");
            }
        });

        handler.<Context>register("submitmap", "Submit a map to be added to the server playlist (will be reviewed by a moderator automatically). Must attach a valid .msav file.", (args, ctx) -> {
            Attachment attachment = (ctx.event.getMessage().getAttachments().size() == 1 ? ctx.event.getMessage().getAttachments().get(0) : null);
            if (attachment == null) {
                ctx.sendEmbed(false, ":link: **you need to attach a valid .msav file!**");
                return;
            }
            File mapFile = new File(assets + attachment.getFileName());
            attachment.downloadToFile(mapFile).thenAccept(file -> {
                Fi fi = new Fi(mapFile);
                byte[] bytes = fi.readBytes();

                DataInputStream dis = new DataInputStream(new InflaterInputStream(new ByteArrayInputStream(bytes)));
                if (attachment.getFileName().endsWith(".msav") && SaveIO.isSaveValid(dis)) {
                    try {

                        OutputStream os = new FileOutputStream(mapFile);
                        os.write(bytes);
                        os.close();

                        ContentHandler.Map map = contentHandler.readMap(fi.read());
                        File imageFile = new File(assets + "image_" + attachment.getFileName().replaceAll(".msav", ".png"));
                        ImageIO.write(map.image, "png", imageFile);


                        EmbedBuilder eb = new EmbedBuilder();
                        eb.setColor(Pals.progress);
                        eb.setTitle(escapeCharacters(map.name));
                        eb.setDescription(map.description);
                        eb.setAuthor(ctx.author.getAsTag(), null, ctx.author.getAvatarUrl());
                        eb.setFooter("react to this message accordingly to approve/disapprove this map.");
                        eb.setImage("attachment://" + imageFile.getName());
                        
                        mapSubmissions.sendFile(mapFile).addFile(imageFile).embed(eb.build()).queue(message -> {
                            message.addReaction("YES:735555385934741554").queue();
                            message.addReaction("NO:735554784534462475").queue();
                        });

                        ctx.sendEmbed(true, ":map: **" + escapeCharacters(map.name) + "** submitted successfully!", "a moderator will soon approve or disapprove your map.");
                    } catch (Exception e) {
                        e.printStackTrace();
                        ctx.sendEmbed(false, ":interrobang: **attachment invalid or corrupted!**");
                    }
                } else {
                    ctx.sendEmbed(false, ":interrobang: **attachment invalid or corrupted!**");
                }
            });
        });

        handler.<Context>register("players","Get all online in-game players.", (args, ctx) -> {
            EmbedBuilder eb = new EmbedBuilder();
            eb.setColor(Pals.progress);
            eb.setTitle(":satellite: **players online: **" + Groups.player.size());

            StringBuilder s = new StringBuilder();
            int pn = 1;
            for(Player p : Groups.player){
                s
                        .append("**")
                        .append(pn + "•")
                        .append("** `")
                        .append(escapeCharacters(p.name))
                        .append("`")
                        .append(" : ")
                        .append(p.id)
                        .append("\n");
                pn++;
            }
            eb.setDescription(s);
            ctx.sendEmbed(eb);
        });

        handler.<Context>register("status", "View the status of this server.", (args, ctx) -> {
            HashMap<String, String> fields = new HashMap<>();
            fields.put("players", String.valueOf(Groups.player.size()));
            fields.put("map", escapeCharacters(state.map.name()));
            fields.put("wave", String.valueOf(state.wave));

            ctx.sendEmbed(true, ":desktop: **" + serverName + "**", fields, false);
        });

        handler.<Context>register("help", "[command]", "Display help for a specified command, or all commands.", (args, ctx) -> {
            EmbedBuilder eb = new EmbedBuilder();
            eb.setTitle(":newspaper: all available commands");
            eb.setDescription("use help [command] to view more information about a command.");
            eb.setColor(Pals.progress);
            if(args.length <= 0) {
                StringBuilder admin = new StringBuilder();
                StringBuilder mod = new StringBuilder();
                StringBuilder reviewer = new StringBuilder();
                StringBuilder publics = new StringBuilder();
                for (Command cmd : bt.moderatorHandler.getCommandList()) {
                    mod.append("**").append(cmd.text).append("**").append(" ").append(cmd.paramText).append("\n");
                }
                for (Command cmd : bt.reviewerHandler.getCommandList()) {
                    reviewer.append("**").append(cmd.text).append("**").append(" ").append(cmd.paramText).append("\n");
                }
                for (Command cmd : bt.publicHandler.getCommandList()) {
                    publics.append("**").append(cmd.text).append("**").append(" ").append(cmd.paramText).append("\n");
                }
                eb.addField("Moderation", mod.toString(), true);
                eb.addField("Maps", reviewer.toString(), true);
                eb.addField("Public", publics.toString(), true);
                ctx.channel.sendMessage(eb.build()).queue();
            }else{
                Command cmd = null;
                for(Command c : bt.moderatorHandler.getCommandList()){
                    if(c.text.equals(args[0].toLowerCase())) cmd = c;
                }
                for(Command c : bt.reviewerHandler.getCommandList()){
                    if(c.text.equals(args[0].toLowerCase())) cmd = c;
                }
                for(Command c : bt.publicHandler.getCommandList()){
                    if(c.text.equals(args[0].toLowerCase())) cmd = c;
                }
                if(cmd != null){
                    ctx.sendEmbed(true, ":gear: " + cmd.text + (cmd.paramText.length() > 0 ? " *" + cmd.paramText + "*" : ""), cmd.description);
                }else{
                    ctx.sendEmbed(false, ":interrobang: that command doesn't exist!");
                }
            }
        });
    }
}
