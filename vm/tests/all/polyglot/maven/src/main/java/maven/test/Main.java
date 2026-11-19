package maven.test;

import org.graalvm.polyglot.*;
import java.util.*;

public class Main {

    private static final String OPTION_DO_NOT_INITIALIZE = "--do-not-initialize=";
    private static final String OPTION_ISOLATED = "--isolated";

    public static void main(String[] args) {
        Options options = Options.parse(args);
        List<String> ids = options.ids;
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("At least one component id must be given as an argument");
        }
        Context.Builder builder;
        if (options.isolated()) {
            // In the polyglot isolate test all passed ids are language ids
            // It's safe to pass them to Context#newBuilder
            builder = Context.newBuilder(options.ids().toArray(new String[0]));
        } else {
            // The passed ids may be languages or tools ids
            builder = Context.newBuilder();
        }
        builder.allowAllAccess(true);
        try (Context ctx = builder.options(requiredOptions(options)).build()) {
            Set<String> languages = ctx.getEngine().getLanguages().keySet();
            Set<String> tools = ctx.getEngine().getInstruments().keySet();
            for (String id : ids) {
                if (languages.contains(id)) {
                    // exists
                    if (!options.incompleteLanguages.contains(id)) {
                        ctx.initialize(id);

                        switch (id) {
                            case "python":
                                ctx.eval("python", "import typing");
                                break;
                            case "js":
                                ctx.eval("js",
                                        "(function (a,b){ return Math.sqrt(a*a + b*b)})(3,4)");
                                break;
                            case "java":
                                break;
                        }

                    }
                } else if (tools.contains(id)) {
                    // exists
                } else {
                    throw new NoSuchElementException(id);
                }
            }
        }
    }

    private static Map<String,String> requiredOptions(Options options) {
        Map<String,String> contextOptions = new HashMap<>();
        Engine.Builder engineBuilder;
        if (options.isolated()) {
            contextOptions.put("engine.SpawnIsolate", "true");
            engineBuilder = Engine.newBuilder(options.ids().toArray(new String[0]));
            engineBuilder.option("engine.SpawnIsolate", "true");
        } else {
            engineBuilder = Engine.newBuilder();
        }

        try (Engine engine = engineBuilder.allowExperimentalOptions(true).build()) {
            Language llvm = engine.getLanguages().get("llvm");
            if (llvm != null && llvm.getOptions().get("llvm.managed") != null
                    && engine.getLanguages().size() == 1) { // only if llvm is exclusively used
                contextOptions.put("llvm.managed", "true");
            }
        }
        return contextOptions;
    }

    record Options (Set<String> incompleteLanguages, boolean isolated, List<String> ids) {

        static Options parse(String[] args) {
            List<String> ids = new ArrayList<>();
            Set<String> incompleteLanguages = new HashSet<>();
            boolean isolated = false;
            for (String arg : args) {
                if (arg.startsWith(OPTION_DO_NOT_INITIALIZE)) {
                    Collections.addAll(incompleteLanguages, arg.substring(OPTION_DO_NOT_INITIALIZE.length()).split(","));
                } else if (arg.equals(OPTION_ISOLATED)) {
                    isolated = true;
                } else {
                    ids.add(arg);
                }
            }
            return new Options(incompleteLanguages, isolated, ids);
        }
    }
}
