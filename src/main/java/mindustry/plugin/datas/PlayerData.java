package mindustry.plugin.datas;

import mindustry.entities.bullet.BulletType;
import mindustry.world.Tile;

import java.util.HashMap;

public class PlayerData{
    public int highestWave;
    public String tag;
    public Tile tapTile;
    public boolean inspector;
    public boolean canInteract = true;

    public BulletType bt;
    public float sclVelocity;
    public float sclLifetime;
    public float sclDamage;

    public int rank;
    public int playTime = 0;
    public int buildingsBuilt = 0;
    public int gamesPlayed = 0;
    public boolean verified = false;
    public boolean frozen = false;
    public boolean banned = false;
    public long bannedUntil = 0;
    public String banReason = "";

    public PlayerData(int rank) { this.rank = rank; }
}
