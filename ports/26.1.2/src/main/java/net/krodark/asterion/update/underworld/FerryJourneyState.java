package net.krodark.asterion.update.underworld;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.update.underworld.entity.CharonsFerryEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import java.util.HashMap;
import java.util.Map;

/** Saved deck coordinates survive logout and server restarts while the ferry moves. */
public final class FerryJourneyState extends SavedData {
    public record Seat(double x,double z,float yaw,boolean paid) {
        public static final Codec<Seat> CODEC=RecordCodecBuilder.create(i->i.group(
                Codec.DOUBLE.fieldOf("x").forGetter(Seat::x),Codec.DOUBLE.fieldOf("z").forGetter(Seat::z),
                Codec.FLOAT.fieldOf("yaw").forGetter(Seat::yaw),Codec.BOOL.fieldOf("paid").forGetter(Seat::paid)).apply(i,Seat::new));
    }
    private static final Codec<FerryJourneyState> CODEC=RecordCodecBuilder.create(i->i.group(
            Codec.unboundedMap(Codec.STRING,Seat.CODEC).optionalFieldOf("seats",Map.of()).forGetter(s->s.seats),
            Codec.DOUBLE.optionalFieldOf("boat_x",0.0).forGetter(s->s.boatX),
            Codec.DOUBLE.optionalFieldOf("boat_z",58.0).forGetter(s->s.boatZ)).apply(i,FerryJourneyState::new));
    private static final SavedDataType<FerryJourneyState> TYPE=new SavedDataType<>(Asterion.id("ferry_journeys"),
            FerryJourneyState::new,CODEC,null);
    public final Map<String,Seat> seats;
    public double boatX,boatZ;
    public FerryJourneyState() { this(Map.of(),0,58); }
    private FerryJourneyState(Map<String,Seat> seats,double x,double z) { this.seats=new HashMap<>(seats);boatX=x;boatZ=z; }
    public static FerryJourneyState get(ServerLevel level) { return level.getDataStorage().computeIfAbsent(TYPE); }
    public void track(CharonsFerryEntity boat) {
        if(seats.isEmpty())return;
        if(Math.abs(boatX-boat.getX())+Math.abs(boatZ-boat.getZ())>.5) { boatX=boat.getX();boatZ=boat.getZ();setDirty(); }
    }
}
