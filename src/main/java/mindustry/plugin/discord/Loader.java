package mindustry.plugin.discord;

import arc.Core;
import arc.util.Log;
import com.google.gson.Gson;
import mindustry.Vars;
import mindustry.plugin.BotThread;
import mindustry.plugin.datas.ContentHandler;
import mindustry.plugin.utils.Funcs;
import mindustry.world.Block;
import mindustry.world.blocks.environment.OreBlock;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.TextChannel;
import org.json.JSONObject;
import org.json.JSONTokener;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

public class Loader {
    public static JedisPool pool;
    public static Gson gson = new Gson();

    public static BotThread bt;
    public static ContentHandler contentHandler;
    public static JDA api = null;
    public static String prefix = ".";
    public static String serverName = "<untitled>";

    public static Guild server;

    public static TextChannel mapSubmissions;
    public static TextChannel warnings;

    public static Role mapreviewer;
    public static Role moderator;
    public static Role administrator;

    public static JSONObject data;
    public static String apiKey = "";
    public static int minRank = 0;

    public static TextChannel getTextChannel(String id){
        return server.getTextChannelById(id);
    }

    public static void load(){
        Funcs.init();


        Log.info("<.io> loading");
        try {
            String pureJson = Core.settings.getDataDirectory().child("mods/settings.json").readString();
            data = new JSONObject(new JSONTokener(pureJson));
        } catch (Exception e) {
            Log.err("Couldn't read settings.json file.");
        }
        try {
            //api = new JDABuilder(data.getString("token")).build().awaitReady();
            api = JDABuilder.createDefault(data.getString("token")).build().awaitReady();
        }catch (Exception e){
            Log.err("Couldn't log into discord.");
        }
        bt = new BotThread(api, Thread.currentThread(), data);
        bt.setDaemon(false);
        bt.start();

        // database
        try {
            pool = new JedisPool(new JedisPoolConfig(), "localhost");
        } catch (Exception e){
            e.printStackTrace();
            Core.app.exit();
        }

        // setup prefix
        if (data.has("prefix")) {
            prefix = String.valueOf(data.getString("prefix").charAt(0));
            bt.publicHandler.setPrefix(prefix);
            bt.reviewerHandler.setPrefix(prefix);
            bt.moderatorHandler.setPrefix(prefix);
        } else {
            Log.warn("Prefix not found, using default '.' prefix.");
        }

        // setup name
        if (data.has("server_name")) {
            serverName = String.valueOf(data.getString("server_name"));
        } else {
            Log.warn("No server name setting detected!");
        }

        // setup anti vpn
        if(data.has("api_key")){
            apiKey = data.getString("api_key");
        }

        // setup minimum rank
        if(data.has("min_rank")){
            minRank = data.getInt("min_rank");
        } else{
            Log.warn("No min_rank found! Setting to default: 0");
        }


        // setup server
        if(data.has("server_id")) {
            server = api.getGuildById(data.getString("server_id"));
        }

        // setup channels
        if(data.has("mapSubmissions_id")) {
            mapSubmissions = getTextChannel(data.getString("mapSubmissions_id"));
        }
        if(data.has("warnings_chat_channel_id")) {
            warnings = getTextChannel(data.getString("warnings_chat_channel_id"));
        }

        // setup roles
        if (data.has("mapSubmissions_roleid")) mapreviewer = api.getRoleById(data.getString("mapSubmissions_roleid"));
        if (data.has("moderator_roleid")) moderator = api.getRoleById(data.getString("moderator_roleid"));
        if (data.has("administrator_roleid")) administrator = api.getRoleById(data.getString("administrator_roleid"));

        Log.info("<.io> loaded successfully");
    }
}
