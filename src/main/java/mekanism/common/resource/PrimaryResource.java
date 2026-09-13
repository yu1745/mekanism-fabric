package mekanism.common.resource;

import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;

public enum PrimaryResource implements IResource {
    IRON("iron", 0xFFAF8E77, true, null, null),
    GOLD("gold", 0xFFF2CD67, true, null, null),
    OSMIUM("osmium", 0xFF1E79C3, false, BlockResourceInfo.OSMIUM, BlockResourceInfo.RAW_OSMIUM),
    COPPER("copper", 0xFFAA4B19, true, null, null),
    TIN("tin", 0xFFCCCCD9, false, BlockResourceInfo.TIN, BlockResourceInfo.RAW_TIN),
    LEAD("lead", 0xFF3A404A, false, BlockResourceInfo.LEAD, BlockResourceInfo.RAW_LEAD),
    URANIUM("uranium", 0xFF46664F, false, BlockResourceInfo.URANIUM, BlockResourceInfo.RAW_URANIUM);

    private final String name;
    private final int tint;
    //Note: This is a supplier because of the chicken and egg of referencing OreType and OreType referencing PrimaryResource
    private final Supplier<TagKey<Item>> oreTag;
    private final boolean isVanilla;
    private final BlockResourceInfo resourceBlockInfo;
    private final BlockResourceInfo rawResourceBlockInfo;

    PrimaryResource(String name, int tint, boolean isVanilla, BlockResourceInfo resourceBlockInfo, BlockResourceInfo rawResourceBlockInfo) {
        this.name = name;
        this.tint = tint;
        this.oreTag = () -> TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "ores/" + name));
        this.isVanilla = isVanilla;
        this.resourceBlockInfo = resourceBlockInfo;
        this.rawResourceBlockInfo = rawResourceBlockInfo;
    }

    @Override
    public String getRegistrySuffix() {
        return name;
    }

    public int getTint() {
        return tint;
    }

    public TagKey<Item> getOreTag() {
        return oreTag.get();
    }

    public boolean has(ResourceType type) {
        return type != ResourceType.ENRICHED && (!isVanilla || !type.isVanilla());
    }

    public boolean isVanilla() {
        return isVanilla;
    }

    @Nullable
    public BlockResourceInfo getResourceBlockInfo() {
        return resourceBlockInfo;
    }

    @Nullable
    public BlockResourceInfo getRawResourceBlockInfo() {
        return rawResourceBlockInfo;
    }
}
