package io.ruin.data.impl.pets;

import com.google.gson.annotations.Expose;
import io.ruin.api.utils.JsonUtils;
import io.ruin.data.DataFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class pet_recolor_exchange extends DataFile {

	@Override
	public String path() {
		return "pets/pet_recolor_exchange.json";
	}

	public static final Map<Integer, List<Variant>> BY_BASE_ITEM_ID = new HashMap<>();
	public static final Map<Integer, Variant> BY_VARIANT_ITEM_ID = new HashMap<>();

	@Override
	public Object fromJson(String fileName, String json) {
		List<Temp> temps = JsonUtils.fromJson(json, List.class, Temp.class);
		BY_BASE_ITEM_ID.clear();
		BY_VARIANT_ITEM_ID.clear();
		for (Temp temp : temps) {
			List<Variant> variants = new ArrayList<>();
			for (VariantTemp vt : temp.variants) {
				Variant variant = new Variant(temp.basePet, temp.baseItemId, vt.theme, vt.itemId, vt.npcId, vt.name, vt.dpCost, vt.vpCost);
				variants.add(variant);
				BY_VARIANT_ITEM_ID.put(vt.itemId, variant);
			}
			BY_BASE_ITEM_ID.put(temp.baseItemId, variants);
		}
		return temps;
	}

	public static final class Variant {
		public final String basePet;
		public final int baseItemId;
		public final String theme;
		public final int itemId;
		public final int npcId;
		public final String name;
		public final int dpCost;
		public final int vpCost;

		public Variant(String basePet, int baseItemId, String theme, int itemId, int npcId, String name, int dpCost, int vpCost) {
			this.basePet = basePet;
			this.baseItemId = baseItemId;
			this.theme = theme;
			this.itemId = itemId;
			this.npcId = npcId;
			this.name = name;
			this.dpCost = dpCost;
			this.vpCost = vpCost;
		}
	}

	public static final class Temp {
		@Expose
		public String basePet;
		@Expose
		public int baseItemId;
		@Expose
		public List<VariantTemp> variants;
	}

	public static final class VariantTemp {
		@Expose
		public String theme;
		@Expose
		public int itemId;
		@Expose
		public int npcId;
		@Expose
		public String name;
		@Expose
		public int dpCost;
		@Expose
		public int vpCost;
	}

}
