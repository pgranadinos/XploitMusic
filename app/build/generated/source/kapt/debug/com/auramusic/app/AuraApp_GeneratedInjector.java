package com.auramusic.app;

import dagger.hilt.InstallIn;
import dagger.hilt.codegen.OriginatingElement;
import dagger.hilt.components.SingletonComponent;
import dagger.hilt.internal.GeneratedEntryPoint;

@OriginatingElement(
    topLevelClass = AuraApp.class
)
@GeneratedEntryPoint
@InstallIn(SingletonComponent.class)
public interface AuraApp_GeneratedInjector {
  void injectAuraApp(AuraApp auraApp);
}
