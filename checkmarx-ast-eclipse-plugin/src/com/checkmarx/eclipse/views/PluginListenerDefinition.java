package com.checkmarx.eclipse.views;

import java.util.List;

public class PluginListenerDefinition {
	
	private PluginListenerType listenerType;
	private List<DisplayModel> resutls;
	
	public PluginListenerDefinition(PluginListenerType listenerType, List<DisplayModel> results) {
		this.listenerType = listenerType;
		this.resutls = results;
	}

	public PluginListenerType getListenerType() {
		return listenerType;
	}

	public void setListenerType(PluginListenerType listenerType) {
		this.listenerType = listenerType;
	}

	public List<DisplayModel> getResutls() {
		return resutls;
	}

	public void setResutls(List<DisplayModel> resutls) {
		this.resutls = resutls;
	}
}
