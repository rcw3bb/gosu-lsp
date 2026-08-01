package xyz.ronella.gosu.lsp.analysis;

import gw.lang.Gosu;
import gw.lang.reflect.IMethodInfo;
import gw.lang.reflect.IPropertyInfo;
import gw.lang.reflect.IType;
import gw.lang.reflect.ITypeInfo;
import gw.lang.reflect.TypeSystem;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * Provides type-member completion items.
 *
 * Phase 2 strategy (two tiers, both fail-safe):
 *   1. Java reflection — for JDK types and any fully-qualified class name.
 *   2. Gosu runtime (gosu-core) — for Gosu-specific types.
 *
 * If Gosu runtime initialisation fails (e.g., Java module restrictions) the
 * provider degrades silently to reflection-only mode.
 */
public class GosuTypeProvider {

    private static final Logger LOG = Logger.getLogger(GosuTypeProvider.class.getName());

    // Guards lazy Gosu runtime initialisation
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicBoolean initFailed  = new AtomicBoolean(false);

    // Maps common Gosu short-names to fully-qualified Java class names
    private static final Map<String, String> JAVA_TYPE_MAP;
    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("String",        "java.lang.String");
        m.put("Integer",       "java.lang.Integer");
        m.put("Long",          "java.lang.Long");
        m.put("Short",         "java.lang.Short");
        m.put("Byte",          "java.lang.Byte");
        m.put("Float",         "java.lang.Float");
        m.put("Double",        "java.lang.Double");
        m.put("Boolean",       "java.lang.Boolean");
        m.put("Character",     "java.lang.Character");
        m.put("Object",        "java.lang.Object");
        m.put("Number",        "java.lang.Number");
        m.put("StringBuilder", "java.lang.StringBuilder");
        m.put("StringBuffer",  "java.lang.StringBuffer");
        m.put("Math",          "java.lang.Math");
        m.put("List",          "java.util.List");
        m.put("ArrayList",     "java.util.ArrayList");
        m.put("LinkedList",    "java.util.LinkedList");
        m.put("Map",           "java.util.Map");
        m.put("HashMap",       "java.util.HashMap");
        m.put("LinkedHashMap", "java.util.LinkedHashMap");
        m.put("Set",           "java.util.Set");
        m.put("HashSet",       "java.util.HashSet");
        m.put("LinkedHashSet", "java.util.LinkedHashSet");
        m.put("Date",          "java.util.Date");
        m.put("BigDecimal",    "java.math.BigDecimal");
        m.put("BigInteger",    "java.math.BigInteger");
        JAVA_TYPE_MAP = Collections.unmodifiableMap(m);
    }

    // ── Public API ────────────────────────────────────────────────────────

    /**
     * Returns completion items representing the public members of {@code typeName}.
     * Returns an empty list if the type cannot be resolved.
     */
    public List<CompletionItem> getMembersOf(String typeName) {
        if (typeName == null || typeName.isBlank()) return Collections.emptyList();

        // Tier 1: Java reflection (covers JDK types, always available)
        List<CompletionItem> items = getMembersViaReflection(typeName);
        if (!items.isEmpty()) return items;

        // Tier 2: Gosu type system (covers Gosu-specific types)
        ensureGosuInitialized();
        if (initialized.get()) {
            return getMembersViaGosu(typeName);
        }

        return Collections.emptyList();
    }

    // ── Initialisation ────────────────────────────────────────────────────

    void ensureGosuInitialized() {
        if (initialized.get() || initFailed.get()) return;
        synchronized (this) {
            if (initialized.get() || initFailed.get()) return;
            try {
                Gosu.init();
                initialized.set(true);
                LOG.info("Gosu type system initialized");
            } catch (Throwable t) {
                initFailed.set(true);
                LOG.warning("Gosu type system init failed (reflection-only mode): " + t.getMessage());
            }
        }
    }

    // ── Tier 1: Java reflection ───────────────────────────────────────────

    private List<CompletionItem> getMembersViaReflection(String typeName) {
        String fqn = JAVA_TYPE_MAP.getOrDefault(typeName, typeName);
        try {
            Class<?> cls = Class.forName(fqn);
            return extractFromClass(cls);
        } catch (ClassNotFoundException | SecurityException e) {
            return Collections.emptyList();
        }
    }

    private static List<CompletionItem> extractFromClass(Class<?> cls) {
        List<CompletionItem> items = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Method m : cls.getMethods()) {
            if (Modifier.isPublic(m.getModifiers())) {
                String label = m.getName() + "()";
                if (seen.add(label)) {
                    CompletionItem item = new CompletionItem(label);
                    item.setKind(CompletionItemKind.Method);
                    item.setDetail(m.getReturnType().getSimpleName());
                    items.add(item);
                }
            }
        }
        for (Field f : cls.getFields()) {
            if (Modifier.isPublic(f.getModifiers())) {
                if (seen.add(f.getName())) {
                    CompletionItem item = new CompletionItem(f.getName());
                    item.setKind(CompletionItemKind.Field);
                    item.setDetail(f.getType().getSimpleName());
                    items.add(item);
                }
            }
        }
        return items;
    }

    // ── Tier 2: Gosu type system ──────────────────────────────────────────

    private static List<CompletionItem> getMembersViaGosu(String typeName) {
        try {
            IType type = TypeSystem.getByFullNameIfValid(typeName);
            if (type == null) return Collections.emptyList();

            ITypeInfo ti = type.getTypeInfo();
            List<CompletionItem> items = new ArrayList<>();

            for (IMethodInfo m : ti.getMethods()) {
                CompletionItem item = new CompletionItem(m.getName() + "()");
                item.setKind(CompletionItemKind.Method);
                items.add(item);
            }

            for (IPropertyInfo p : ti.getProperties()) {
                CompletionItem item = new CompletionItem(p.getName());
                item.setKind(CompletionItemKind.Property);
                items.add(item);
            }

            return items;
        } catch (Throwable t) {
            LOG.fine("Gosu type lookup failed for '" + typeName + "': " + t.getMessage());
            return Collections.emptyList();
        }
    }
}
