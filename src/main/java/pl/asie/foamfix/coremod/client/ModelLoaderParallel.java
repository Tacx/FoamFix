package pl.asie.foamfix.coremod.client;

import com.google.common.collect.*;
import net.minecraft.client.renderer.BlockModelShapes;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelBakery;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.IRegistry;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.client.model.ModelLoaderRegistry;
import pl.asie.foamfix.util.FoamUtils;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ModelLoaderParallel extends ModelBakery {
    private static boolean firstLoad;
    private final Map<ModelResourceLocation, IModel> stateModels = Maps.newHashMap();
    private final Set<ModelResourceLocation> missingVariants = Sets.newHashSet();
    private final Map<ResourceLocation, Exception> loadingExceptions = Maps.newHashMap();
    private IModel missingModel = null;

    private boolean isLoading = false;

    public ModelLoaderParallel(IResourceManager resourceManagerIn, TextureMap textureMapIn, BlockModelShapes blockModelShapesIn) {
        super(resourceManagerIn, textureMapIn, blockModelShapesIn);
    }

    // shim
    protected IModel getMissingModel() {
        return null;
    }

    @Override
    public IRegistry<ModelResourceLocation, IBakedModel> setupModelRegistry() {
        isLoading = true;
        loadBlocks();
        loadVariantItemModels();
        missingModel = ModelLoaderRegistry.getMissingModel();
        stateModels.put(MODEL_MISSING, missingModel);

        try {
            final Set<ResourceLocation> textures = Sets.newHashSet((Iterable<ResourceLocation>) FoamUtils.MLR_GET_TEXTURES.invokeExact());
            textures.remove(TextureMap.LOCATION_MISSING_TEXTURE);
            textures.addAll(LOCATIONS_BUILTIN_TEXTURES);

            ModelLoaderParallelHelper.textures = textures;
            textureMap.loadSprites(resourceManager, ModelLoaderParallelHelper.POPULATOR);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }

        IBakedModel missingBaked = missingModel.bake(missingModel.getDefaultState(), DefaultVertexFormats.ITEM, ModelLoader.defaultTextureGetter());

        if (firstLoad)
        {
            firstLoad = false;
            for (ModelResourceLocation mrl : stateModels.keySet())
            {
                bakedRegistry.putObject(mrl, missingBaked);
            }
            return bakedRegistry;
        }

        Map<IModel, IBakedModel> bakedModels = new ConcurrentHashMap<>();
        HashMultimap<IModel, ModelResourceLocation> models = HashMultimap.create();
        Multimaps.invertFrom(Multimaps.forMap(stateModels), models);

        ModelLoaderParallelHelper.parallelBake(bakedModels, models, getMissingModel(), missingBaked);

        for (Map.Entry<ModelResourceLocation, IModel> e : stateModels.entrySet())
        {
            bakedRegistry.putObject(e.getKey(), bakedModels.get(e.getValue()));
        }
        return bakedRegistry;
    }
}