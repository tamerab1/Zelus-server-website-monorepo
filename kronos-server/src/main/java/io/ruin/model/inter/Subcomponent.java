package io.ruin.model.inter;

public enum Subcomponent {

	// chatbox (162) CHATMODAL / CHATOVERLAY, rev 241 ids
	DIALOGUE(568, ToplevelComponent.CHATBOX),
	YES_NO_DIALOGUE(567, ToplevelComponent.CHATBOX);

	public int getComponent() {
		return component;
	}

	public ToplevelComponent getParent() {
		return parent;
	}

	private final int component;
	private final ToplevelComponent parent;

	Subcomponent(int component, ToplevelComponent parent) {
		this.component = component;
		this.parent = parent;
	}

}
