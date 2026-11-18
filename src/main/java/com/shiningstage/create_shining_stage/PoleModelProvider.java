package com.shiningstage.create_shining_stage;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.server.packs.PackType;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

/** Builds the 64 standalone pole models by merging the existing directional model fragments. */
public class PoleModelProvider implements DataProvider {
    private static final String MOD_ID = CreateShiningStage.MOD_ID;
    private static final List<String> DIRECTIONS = List.of("north", "south", "east", "west", "up", "down");
    private static final Map<String, String> PART_FILES = Map.of(
        "north", "front", "south", "back", "west", "right", "east", "left", "up", "up", "down", "down");

    private final PackOutput output;
    private final ExistingFileHelper existingFileHelper;

    public PoleModelProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        this.output = output;
        this.existingFileHelper = existingFileHelper;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cachedOutput) {
        try {
            JsonObject[] fragments = new JsonObject[7];
            fragments[0] = read("pole/pole_mid");
            for (int i = 0; i < DIRECTIONS.size(); i++) {
                fragments[i + 1] = read("pole/pole_" + PART_FILES.get(DIRECTIONS.get(i)));
            }

            JsonObject blockstate = new JsonObject();
            JsonObject variants = new JsonObject();
            blockstate.add("variants", variants);
            List<CompletableFuture<?>> writes = new ArrayList<>();
            for (int mask = 0; mask < 64; mask++) {
                List<String> selected = new ArrayList<>();
                StringBuilder key = new StringBuilder();
                for (int i = 0; i < DIRECTIONS.size(); i++) {
                    String direction = DIRECTIONS.get(i);
                    if (key.length() > 0) key.append(',');
                    key.append(direction).append('=').append((mask & (1 << i)) != 0);
                    if ((mask & (1 << i)) != 0) selected.add(direction);
                }

                boolean straight = isStraightPair(mask);
                String modelName = "combined" + (selected.isEmpty() ? "_center" : "_" + String.join("_", selected));
                JsonObject model = fragments[0].deepCopy();
                JsonArray elements = new JsonArray();
                if (!straight) elements.addAll(fragments[0].getAsJsonArray("elements"));
                for (int i = 0; i < DIRECTIONS.size(); i++) {
                    if ((mask & (1 << i)) != 0) {
                        elements.addAll(fragments[i + 1].getAsJsonArray("elements"));
                    }
                }
                model.add("elements", elements);
                var modelPath = output.getOutputFolder().resolve("assets/" + MOD_ID + "/models/block/pole/" + modelName + ".json");
                writes.add(DataProvider.saveStable(cachedOutput, model, modelPath));

                JsonObject variant = new JsonObject();
                variant.addProperty("model", MOD_ID + ":block/pole/" + modelName);
                variants.add(key.toString(), variant);
            }

            JsonObject item = new JsonObject();
            item.addProperty("parent", MOD_ID + ":block/tripod/tripod_pole");
            writes.add(DataProvider.saveStable(cachedOutput, blockstate,
                output.getOutputFolder().resolve("assets/" + MOD_ID + "/blockstates/pole.json")));
            writes.add(DataProvider.saveStable(cachedOutput, item,
                output.getOutputFolder().resolve("assets/" + MOD_ID + "/models/item/pole.json")));
            return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
        } catch (IOException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private JsonObject read(String path) throws IOException {
        ResourceLocation location = ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
        try (var reader = new InputStreamReader(
            existingFileHelper.getResource(location, PackType.CLIENT_RESOURCES, ".json", "models/block").open(),
            StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static boolean isStraightPair(int mask) {
        return mask == ((1 << 0) | (1 << 1)) // north + south
            || mask == ((1 << 2) | (1 << 3)) // east + west
            || mask == ((1 << 4) | (1 << 5)); // up + down
    }

    @Override
    public String getName() {
        return "Pole combined models";
    }
}
