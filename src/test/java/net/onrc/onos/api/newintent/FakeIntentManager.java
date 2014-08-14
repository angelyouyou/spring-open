package net.onrc.onos.api.newintent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Fake implementation of the intent service to assist in developing tests
 * of the interface contract.
 */
public class FakeIntentManager implements TestableIntentService {

    private final Map<IntentId, Intent> intents = new HashMap<>();
    private final Map<IntentId, IntentState> intentStates = new HashMap<>();
    private final Map<IntentId, List<InstallableIntent>> installables = new HashMap<>();
    private final Set<IntentEventListener> listeners = new HashSet<>();

    private final Map<Class<? extends Intent>, IntentCompiler<? extends Intent>> compilers = new HashMap<>();
    private final Map<Class<? extends InstallableIntent>,
            IntentInstaller<? extends InstallableIntent>> installers = new HashMap<>();

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<IntentException> exceptions = new ArrayList<>();

    @Override
    public List<IntentException> getExceptions() {
        return exceptions;
    }

    // Provides an out-of-thread simulation of intent submit life-cycle
    private void executeSubmit(final Intent intent) {
        registerSubclassCompilerIfNeeded(intent);
        registerSubclassInstallerIfNeeded((InstallableIntent) intent);
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    List<InstallableIntent> installable = compileIntent(intent);
                    installIntents(intent, installable);
                } catch (IntentException e) {
                    exceptions.add(e);
                }
            }
        });
    }

    // Provides an out-of-thread simulation of intent withdraw life-cycle
    private void executeWithdraw(final Intent intent) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    List<InstallableIntent> installable = getInstallable(intent.getId());
                    uninstallIntents(intent, installable);
                } catch (IntentException e) {
                    exceptions.add(e);
                }

            }
        });
    }

    private <T extends Intent> IntentCompiler<T> getCompiler(T intent) {
        @SuppressWarnings("unchecked")
        IntentCompiler<T> compiler = (IntentCompiler<T>) compilers.get(intent.getClass());
        if (compiler == null) {
            throw new IntentException("no compiler for class " + intent.getClass());
        }
        return compiler;
    }

    private <T extends InstallableIntent> IntentInstaller<T> getInstaller(T intent) {
        @SuppressWarnings("unchecked")
        IntentInstaller<T> installer = (IntentInstaller<T>) installers.get(intent.getClass());
        if (installer == null) {
            throw new IntentException("no installer for class " + intent.getClass());
        }
        return installer;
    }

    private <T extends Intent> List<InstallableIntent> compileIntent(T intent) {
        try {
            // For the fake, we compile using a single level pass
            List<InstallableIntent> installable = new ArrayList<>();
            for (Intent compiled : getCompiler(intent).compile(intent)) {
                installable.add((InstallableIntent) compiled);
            }
            setState(intent, IntentState.COMPILED);
            return installable;
        } catch (IntentException e) {
            setState(intent, IntentState.FAILED);
            throw e;
        }
    }

    private void installIntents(Intent intent, List<InstallableIntent> installable) {
        try {
            for (InstallableIntent ii : installable) {
                getInstaller(ii).install(ii);
            }
            setState(intent, IntentState.INSTALLED);
            putInstallable(intent.getId(), installable);
        } catch (IntentException e) {
            setState(intent, IntentState.FAILED);
            throw e;
        }
    }

    private void uninstallIntents(Intent intent, List<InstallableIntent> installable) {
        try {
            for (InstallableIntent ii : installable) {
                getInstaller(ii).remove(ii);
            }
            setState(intent, IntentState.WITHDRAWN);
            removeInstallable(intent.getId());
        } catch (IntentException e) {
            setState(intent, IntentState.FAILED);
            throw e;
        }
    }


    // Sets the internal state for the given intent and dispatches an event
    private void setState(Intent intent, IntentState state) {
        IntentState previous = intentStates.get(intent.getId());
        intentStates.put(intent.getId(), state);
        dispatch(new IntentEvent(intent, state, previous, System.currentTimeMillis()));
    }

    private void putInstallable(IntentId id, List<InstallableIntent> installable) {
        installables.put(id, installable);
    }

    private void removeInstallable(IntentId id) {
        installables.remove(id);
    }

    private List<InstallableIntent> getInstallable(IntentId id) {
        List<InstallableIntent> installable = installables.get(id);
        if (installable != null) {
            return installable;
        } else {
            return Collections.emptyList();
        }
    }

    @Override
    public void submit(Intent intent) {
        intents.put(intent.getId(), intent);
        setState(intent, IntentState.SUBMITTED);
        executeSubmit(intent);
    }

    @Override
    public void withdraw(Intent intent) {
        intents.remove(intent.getId());
        setState(intent, IntentState.WITHDRAWING);
        executeWithdraw(intent);
    }

    @Override
    public void execute(IntentOperations operations) {
        // TODO: implement later
    }

    @Override
    @SuppressWarnings("unchecked")
    public Set<Intent> getIntents() {
        return Collections.unmodifiableSet(new HashSet<>(intents.values()));
    }

    @Override
    public Intent getIntent(IntentId id) {
        return intents.get(id);
    }

    @Override
    public IntentState getIntentState(IntentId id) {
        return intentStates.get(id);
    }

    @Override
    public void addListener(IntentEventListener listener) {
        listeners.add(listener);
    }

    @Override
    public void removeListener(IntentEventListener listener) {
        listeners.remove(listener);
    }

    private void dispatch(IntentEvent event) {
        for (IntentEventListener listener : listeners) {
            listener.event(event);
        }
    }

    @Override
    public <T extends Intent> void registerCompiler(Class<T> cls, IntentCompiler<T> compiler) {
        compilers.put(cls, compiler);
    }

    @Override
    public <T extends Intent> void unregisterCompiler(Class<T> cls) {
        compilers.remove(cls);
    }

    @Override
    public Map<Class<? extends Intent>, IntentCompiler<? extends Intent>> getCompilers() {
        return Collections.unmodifiableMap(compilers);
    }

    @Override
    public <T extends InstallableIntent> void registerInstaller(Class<T> cls, IntentInstaller<T> installer) {
        installers.put(cls, installer);
    }

    @Override
    public <T extends InstallableIntent> void unregisterInstaller(Class<T> cls) {
        installers.remove(cls);
    }

    @Override
    public Map<Class<? extends InstallableIntent>,
            IntentInstaller<? extends InstallableIntent>> getInstallers() {
        return Collections.unmodifiableMap(installers);
    }

    @SuppressWarnings("unchecked")
    private void registerSubclassCompilerIfNeeded(Intent intent) {
        if (!compilers.containsKey(intent.getClass())) {
            Class<?> cls = intent.getClass();
            while (cls != Object.class) {
                // As long as we're within the Intent class descendants
                if (Intent.class.isAssignableFrom(cls)) {
                    IntentCompiler<?> compiler = compilers.get(cls);
                    if (compiler != null) {
                        compilers.put(intent.getClass(), compiler);
                        return;
                    }
                }
                cls = cls.getSuperclass();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void registerSubclassInstallerIfNeeded(InstallableIntent intent) {
        if (!installers.containsKey(intent.getClass())) {
            Class<?> cls = intent.getClass();
            while (cls != Object.class) {
                // As long as we're within the InstallableIntent class descendants
                if (InstallableIntent.class.isAssignableFrom(cls)) {
                    IntentInstaller<?> installer = installers.get(cls);
                    if (installer != null) {
                        installers.put(intent.getClass(), installer);
                        return;
                    }
                }
                cls = cls.getSuperclass();
            }
        }
    }

}
