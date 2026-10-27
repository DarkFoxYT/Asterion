package net.krodark.asterion.update.underworld.entity;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/** Pure steering rules shared by the crawler and its movement regression checks. */
public final class SpiderSurfaceMotion {
    private SpiderSurfaceMotion() { }
    public static Vec3 stablePursuit(Vec3 previous,Vec3 next) {
        if(previous==null || previous.distanceToSqr(next)>16)return next;
        if(previous.distanceToSqr(next)<.0625)return previous;
        return previous.lerp(next,.65);
    }
    /** Short, bounded pursuit lead; close-range attacks aim at the current body. */
    public static Vec3 intercept(Vec3 hunter,Vec3 prey,Vec3 velocity,double speed) {
        double distance=hunter.distanceTo(prey);
        if(distance<2 || velocity.lengthSqr()<.0001)return prey;
        double ticks=Math.clamp((distance-2)/Math.max(.12,speed),0,6);
        Vec3 lead=velocity.multiply(1,.35,1).scale(ticks);
        if(lead.length()>1.5)lead=lead.normalize().scale(1.5);
        return prey.add(lead);
    }
    public static Vec3 tangent(Direction surface, Vec3 vector) {
        Vec3 normal = surface.getUnitVec3();
        return vector.subtract(normal.scale(vector.dot(normal)));
    }
    public static Vec3 heading(Direction surface, Vec3 desired, Vec3 previous, boolean ascending) {
        Vec3 target = tangent(surface, desired);
        if (ascending && surface.getAxis().isHorizontal())
            target = new Vec3(target.x, 0, target.z).normalize().scale(.35).add(0, 1, 0);
        if (target.lengthSqr() < .04) target = tangent(surface, previous);
        if (target.lengthSqr() < .001)
            target = surface.getAxis().isHorizontal() ? new Vec3(0,1,0) : new Vec3(0,0,1);
        return target.normalize();
    }
    public static Vec3 cornerHeading(Direction previous, Direction next, Vec3 heading) {
        Vec3 projected = tangent(next, heading);
        // At an inside corner the old heading points into the new support.
        // Continue away from the old wall instead of stopping or turning back onto it.
        return projected.lengthSqr() > .04 ? projected.normalize() : previous.getUnitVec3().scale(-1);
    }
    /** Parallel transport preserves heading through a changing support frame. */
    public static Vec3 transport(Vec3 previousNormal, Vec3 nextNormal, Vec3 heading) {
        Vec3 a=previousNormal.normalize(),b=nextNormal.normalize();
        Vec3 axis=a.cross(b);
        double sine=axis.length(),cosine=Math.clamp(a.dot(b),-1,1);
        Vec3 transported=heading;
        if(sine>.00001) {
            axis=axis.scale(1/sine);
            transported=heading.scale(cosine).add(axis.cross(heading).scale(sine))
                    .add(axis.scale(axis.dot(heading)*(1-cosine)));
        }
        transported=transported.subtract(b.scale(transported.dot(b)));
        if(transported.lengthSqr()<.00001) {
            Vec3 fallback=Math.abs(b.y)<.9?new Vec3(0,1,0):new Vec3(0,0,1);
            transported=fallback.subtract(b.scale(fallback.dot(b)));
        }
        return transported.normalize();
    }

    public static Vec3 turn(Vec3 normal,Vec3 previous,Vec3 desired,double maxAngle) {
        Vec3 from=previous.subtract(normal.scale(previous.dot(normal))).normalize();
        Vec3 to=desired.subtract(normal.scale(desired.dot(normal))).normalize();
        if(to.lengthSqr()<.001)return from;
        if(from.lengthSqr()<.001)return to;
        double angle=Math.atan2(normal.dot(from.cross(to)),Math.clamp(from.dot(to),-1,1));
        angle=Math.clamp(angle,-maxAngle,maxAngle);
        return from.scale(Math.cos(angle)).add(normal.cross(from).scale(Math.sin(angle))).normalize();
    }
}
