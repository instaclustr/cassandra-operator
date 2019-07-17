package com.instaclustr.cassandra.backup.guice;

import static com.google.inject.multibindings.MapBinder.newMapBinder;

import com.google.inject.Binder;
import com.google.inject.TypeLiteral;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.util.Types;
import com.instaclustr.cassandra.backup.impl.backup.Backuper;
import com.instaclustr.cassandra.backup.impl.restore.Restorer;

public class BackupRestoreBindings {

    public static <RESTORER extends Restorer, BACKUPER extends Backuper>
    void installBindings(final Binder binder,
                         final String typeId,
                         final Class<RESTORER> restorerClass,
                         final Class<BACKUPER> backuperClass) {

        @SuppressWarnings("unchecked")
        final TypeLiteral<RestorerFactory<RESTORER>> restorerFactoryType =
                (TypeLiteral<RestorerFactory<RESTORER>>) TypeLiteral.get(Types.newParameterizedType(RestorerFactory.class, restorerClass));

        @SuppressWarnings("unchecked")
        final TypeLiteral<BackuperFactory<BACKUPER>> backuperFactoryType =
                (TypeLiteral<BackuperFactory<BACKUPER>>) TypeLiteral.get(Types.newParameterizedType(BackuperFactory.class, backuperClass));

        binder.install(new FactoryModuleBuilder()
                               .implement(Restorer.class, restorerClass)
                               .build(restorerFactoryType));

        binder.install(new FactoryModuleBuilder()
                               .implement(Backuper.class, backuperClass)
                               .build(backuperFactoryType));

        // add an entry to the Map<String, RestorerFactory> for the factory created above
        MapBinder.newMapBinder(binder, TypeLiteral.get(String.class), TypeLiteral.get(RestorerFactory.class))
                .addBinding(typeId).to(restorerFactoryType);

        // add an entry to the Map<String, BackuperFactory> for the factory created above
        MapBinder.newMapBinder(binder, TypeLiteral.get(String.class), TypeLiteral.get(BackuperFactory.class))
                .addBinding(typeId).to(backuperFactoryType);

        // Map<String, RestorerFactory>
        newMapBinder(binder,
                     new TypeLiteral<String>() {},
                     restorerFactoryType)
                .addBinding(typeId).to(restorerFactoryType);

        // Map<String, BackuperFactory>
        newMapBinder(binder,
                     new TypeLiteral<String>() {},
                     backuperFactoryType)
                .addBinding(typeId).to(backuperFactoryType);

        Multibinder.newSetBinder(binder, String.class, StorageProviders.class).addBinding().toInstance(typeId);
    }
}
