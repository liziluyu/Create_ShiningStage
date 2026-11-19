package com.shiningstage.create_shining_stage;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

/** Checks the actual compiled hook against early exits without loading Minecraft or a mod bus. */
public final class StageLightingHookRegression {
    private static final String PACKAGE = "com/shiningstage/create_shining_stage/";

    public static void main(String[] args) throws IOException {
        ClassNode fixture = read(PACKAGE + "SpotlightBlockEntity");
        MethodNode tick = fixture.methods.stream().filter(method -> method.name.equals("tick")
            && method.desc.equals("()V")).findFirst().orElseThrow();
        long exits = java.util.stream.StreamSupport.stream(tick.instructions.spliterator(), false)
            .filter(instruction -> instruction.getOpcode() == Opcodes.RETURN).count();
        if (exits < 2) throw new AssertionError("Expected the client/empty-migration early exit in fixture tick");

        ClassNode mixin = read(PACKAGE + "mixin/ClientSpotlightBlockEntityMixin");
        MethodNode registration = mixin.methods.stream().filter(method ->
            java.util.stream.StreamSupport.stream(method.instructions.spliterator(), false)
                .anyMatch(instruction -> instruction instanceof org.objectweb.asm.tree.MethodInsnNode call
                    && call.owner.equals(PACKAGE + "ClientStageLighting") && call.name.equals("register")))
            .findFirst().orElseThrow();
        AnnotationNode injection = registration.visibleAnnotations.stream()
            .filter(annotation -> annotation.desc.equals("Lorg/spongepowered/asm/mixin/injection/Inject;"))
            .findFirst().orElseThrow();
        List<?> targets = (List<?>) value(injection, "method");
        if (!targets.contains("tick()V")) throw new AssertionError("Registration no longer targets fixture tick");
        @SuppressWarnings("unchecked")
        List<AnnotationNode> points = (List<AnnotationNode>) value(injection, "at");
        boolean allPaths = points.stream().map(point -> value(point, "value"))
            .anyMatch(point -> point.equals("HEAD") || point.equals("RETURN"));
        if (!allPaths) {
            throw new AssertionError("Client lighting registration misses early tick exits (" + exits
                + " returns); TAIL only reaches the final return, after the server-only migration");
        }
        System.out.println("Stage lighting hook regression passed: client early exits retain registration");
    }

    private static Object value(AnnotationNode annotation, String name) {
        for (int i = 0; i < annotation.values.size(); i += 2) {
            if (annotation.values.get(i).equals(name)) return annotation.values.get(i + 1);
        }
        throw new AssertionError("Missing annotation value: " + name);
    }

    private static ClassNode read(String name) throws IOException {
        try (InputStream input = StageLightingHookRegression.class.getClassLoader().getResourceAsStream(name + ".class")) {
            if (input == null) throw new AssertionError("Missing compiled class: " + name);
            ClassNode node = new ClassNode();
            new ClassReader(input).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return node;
        }
    }
}
