package rk.commons.ioc.factory.support;

import rk.commons.ioc.factory.config.ObjectDefinition;

public interface ObjectDefinitionRegistry {

	void registerObjectDefinition(ObjectDefinition definition);
	
	void removeObjectDefinition(String objectQName);
	
	ObjectDefinition getObjectDefinition(String objectQName);
	
	boolean containsObjectDefinition(String objectQName);
	
	String[] getObjectQNames();
}
