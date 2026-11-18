package io.ruin.model;

public class ScrollbarClientScript extends ClientScriptBuilder {

	private int interfaceId;
	private int containerId;
	private int childrenCount = 0;
	private int scrollbarChildId;
	private boolean scrollToEnd = false;

	private int fillGraphicId = 0;
	private int topGraphicId;
	private int bottomGraphicId;

	private int scrollTopGraphicId = 0;
	private int scrollSidesGraphicId = 0;
	private int scrollBottomGraphicId = 0;

	public static ScrollbarClientScript create() {
		return new ScrollbarClientScript();
	}

	private ScrollbarClientScript() {
		super(10532);
	}

	public ScrollbarClientScript interfaceId(int interfaceId) {
		this.interfaceId = interfaceId;
		return this;
	}

	public ScrollbarClientScript containerId(int containerId) {
		this.containerId = containerId;
		return this;
	}

	public ScrollbarClientScript scrollbarChildId(int scrollbarChildId) {
		this.scrollbarChildId = scrollbarChildId;
		return this;
	}

	public ScrollbarClientScript childrenCount(int childrenCount) {
		this.childrenCount = childrenCount;
		return this;
	}

	public ScrollbarClientScript scrollToTheEnd(boolean value) {
		this.scrollToEnd = value;
		return this;
	}

	public ScrollbarClientScript withDefaultGraphics() {
		this.fillGraphicId = 798;
		this.scrollTopGraphicId = 795;
		this.scrollSidesGraphicId = 796;
		this.scrollBottomGraphicId = 797;
		this.topGraphicId = 793;
		this.bottomGraphicId = 794;
		return this;
	}

	/** The Karn scrollbar (cache stage 30, assets/karn_ui): crimson dragger with gold rims, gold-chevron arrows. */
	public ScrollbarClientScript withKarnGraphics() {
		// round 2 (2026-11-12): the slim crimson thumb in a dark groove (MakeKarnUi2.scrollbar)
		this.fillGraphicId = 24857;
		this.scrollTopGraphicId = 24854;
		this.scrollSidesGraphicId = 24855;
		this.scrollBottomGraphicId = 24856;
		this.topGraphicId = 24852;
		this.bottomGraphicId = 24853;
		return this;
	}

	/** the ornate set cut from the user's reference art (2026-11-14, ref/build_perks.py): Perks, Karn Upgrader, Karn Workbench */
	public ScrollbarClientScript withOrnateGraphics() {
		this.fillGraphicId = 25182;
		this.scrollTopGraphicId = 25179;
		this.scrollSidesGraphicId = 25180;
		this.scrollBottomGraphicId = 25181;
		this.topGraphicId = 25177;
		this.bottomGraphicId = 25178;
		return this;
	}

	/** the light parchment set (2026-11-14, KarnParchment.scrollbar): Perks, Relic Forge, Karn Forge */
	public ScrollbarClientScript withParchmentGraphics() {
		this.fillGraphicId = 25176;
		this.scrollTopGraphicId = 25173;
		this.scrollSidesGraphicId = 25174;
		this.scrollBottomGraphicId = 25175;
		this.topGraphicId = 25171;
		this.bottomGraphicId = 25172;
		return this;
	}

	public ScrollbarClientScript withDarkGraphics() {
		this.fillGraphicId = 792;
		this.scrollTopGraphicId = 789;
		this.scrollSidesGraphicId = 790;
		this.scrollBottomGraphicId = 791;
		this.topGraphicId = 773;
		this.bottomGraphicId = 788;
		return this;
	}

	public ScrollbarClientScript build() {
		this.addInt(this.scrollToEnd ? 0 : 1);
		this.addInt(this.childrenCount);
		this.addInt(this.interfaceId << 16 | this.containerId);
		this.addInt(this.interfaceId << 16 | this.scrollbarChildId);
		this.addInt(this.fillGraphicId);
		this.addInt(this.scrollTopGraphicId);
		this.addInt(this.scrollSidesGraphicId);
		this.addInt(this.scrollBottomGraphicId);
		this.addInt(this.topGraphicId);
		this.addInt(this.bottomGraphicId);
		return this;
	}

}

