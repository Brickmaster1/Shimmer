package com.lowdragmc.shimmer.comp.vs;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;

/**
 * Optional runtime bridge to Valkyrien Skies to transform ship-space
 * positions into world-space. Pure reflection; if VS isn't present this is a no-op.
 */
public final class VSBridge {
    private static final boolean VS_PRESENT;
    private static final MethodHandle GET_SHIP_BY_BLOCKPOS; // (ClientLevel, BlockPos) -> ClientShip?
    private static final MethodHandle GET_SHIP_BY_DOUBLES;  // (ClientLevel, double,double,double) -> ClientShip?
    private static final MethodHandle GET_RENDER_TRANSFORM; // (ClientShip) -> ShipTransform
    private static final MethodHandle GET_SHIP_TO_WORLD;    // (ShipTransform) -> Matrix4d/Matrix4dc
    private static final MethodHandle TRANSFORM_POSITION;   // (Matrix4d*, Vector3d) -> Vector3d/Vector3dc

    static {
        boolean present = false;
        MethodHandle byPos = null, byDoubles = null, renderTransform = null, shipToWorld = null, transformPos = null;
        try {
            // If this class loads, VS is on the classpath.
            Class.forName("org.valkyrienskies.mod.common.VSGameUtilsKt");
            present = true;

            final MethodHandles.Lookup lk = MethodHandles.lookup();
            final Class<?> utils = Class.forName("org.valkyrienskies.mod.common.VSGameUtilsKt");

            // Find either getShipObjectManagingPos(level, BlockPos) or (level, double,double,double)
            for (Method m : utils.getDeclaredMethods()) {
                if (!m.getName().equals("getShipObjectManagingPos")) continue;
                var p = m.getParameterTypes();
                if (p.length == 2 && p[0].getName().equals("net.minecraft.client.multiplayer.ClientLevel")
                                 && p[1].getName().equals("net.minecraft.core.BlockPos")) {
                    byPos = lk.unreflect(m);
                } else if (p.length == 4 && p[0].getName().equals("net.minecraft.client.multiplayer.ClientLevel")
                                        && p[1] == double.class && p[2] == double.class && p[3] == double.class) {
                    byDoubles = lk.unreflect(m);
                }
            }

            // ship.getRenderTransform()
            var clientShip = Class.forName("org.valkyrienskies.core.api.ships.ClientShip");
            renderTransform = lk.unreflect(clientShip.getMethod("getRenderTransform"));

            // Either getShipToWorldMatrix() or getShipToWorld()
            var shipTransform = Class.forName("org.valkyrienskies.core.api.ships.properties.ShipTransform");
            Method mShipToWorld;
            try { mShipToWorld = shipTransform.getMethod("getShipToWorldMatrix"); }
            catch (NoSuchMethodException ignored) { mShipToWorld = shipTransform.getMethod("getShipToWorld"); }
            shipToWorld = lk.unreflect(mShipToWorld);

            // matrix.transformPosition(Vector3d)
            var matrix4d = Class.forName("org.joml.Matrix4d");
            transformPos = lk.unreflect(matrix4d.getMethod("transformPosition", Vector3d.class));
        } catch (Throwable ignored) { present = false; }

        VS_PRESENT = present;
        GET_SHIP_BY_BLOCKPOS = byPos;
        GET_SHIP_BY_DOUBLES  = byDoubles;
        GET_RENDER_TRANSFORM = renderTransform;
        GET_SHIP_TO_WORLD    = shipToWorld;
        TRANSFORM_POSITION   = transformPos;
    }

    public static boolean active() {
        return VS_PRESENT &&
               (GET_SHIP_BY_BLOCKPOS != null || GET_SHIP_BY_DOUBLES != null) &&
               GET_RENDER_TRANSFORM != null && GET_SHIP_TO_WORLD != null && TRANSFORM_POSITION != null;
    }

    /**
     * If (x,y,z) is on a VS ship, returns world-space coords; otherwise null.
     */
    public static @Nullable Vector3d shipToWorldIfOnShip(ClientLevel level, double x, double y, double z) {
        if (!active() || level == null) return null;
        try {
            Object ship = null;
            if (GET_SHIP_BY_BLOCKPOS != null) {
                ship = GET_SHIP_BY_BLOCKPOS.invoke(level, new BlockPos((int)Math.floor(x), (int)Math.floor(y), (int)Math.floor(z)));
            }
            if (ship == null && GET_SHIP_BY_DOUBLES != null) {
                ship = GET_SHIP_BY_DOUBLES.invoke(level, x, y, z);
            }
            if (ship == null) return null;

            var renderTransform = GET_RENDER_TRANSFORM.invoke(ship);
            var matrix = GET_SHIP_TO_WORLD.invoke(renderTransform);

            var v = new org.joml.Vector3d(x, y, z);
            TRANSFORM_POSITION.invoke(matrix, v);
            return v;
        } catch (Throwable t) {
            return null;
        }
    }

    private VSBridge() {}
}
