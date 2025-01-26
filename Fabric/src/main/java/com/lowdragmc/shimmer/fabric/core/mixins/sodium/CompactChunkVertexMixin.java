package com.lowdragmc.shimmer.fabric.core.mixins.sodium;

import me.jellysquid.mods.sodium.client.render.chunk.terrain.material.Material;
import me.jellysquid.mods.sodium.client.render.chunk.vertex.format.ChunkVertexEncoder;
import me.jellysquid.mods.sodium.client.render.chunk.vertex.format.impl.CompactChunkVertex;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = CompactChunkVertex.class, remap = false)
public abstract class CompactChunkVertexMixin {
    @Redirect(method = "lambda$getEncoder$0", at = @At(value = "INVOKE", target = "Lme/jellysquid/mods/sodium/client/render/chunk/terrain/material/Material;bits()I"))
    private static int injectMaterialForBloom(Material material, long ptr, Material material1, ChunkVertexEncoder.Vertex vertex, int i) {
        var origin = material.bits();
        if ((vertex.light & 0x100) != 0) {
            origin |= (0x01 << 4);
        }
        return origin;
    }

    @ModifyArg(method = "lambda$getEncoder$0", at = @At(value = "INVOKE", target = "Lorg/lwjgl/system/MemoryUtil;memPutInt(JI)V", ordinal = 1), index = 1)
    private static int injectLightForBloom(int light) {
        if ((light & 0x100) != 0) {
            return 15 | 15 << 4;
        }
        return light;
    }
}
