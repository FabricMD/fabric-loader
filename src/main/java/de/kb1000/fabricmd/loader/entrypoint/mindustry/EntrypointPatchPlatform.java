package de.kb1000.fabricmd.loader.entrypoint.mindustry;

import net.fabricmc.loader.entrypoint.EntrypointPatch;
import net.fabricmc.loader.entrypoint.EntrypointTransformer;
import net.fabricmc.loader.launch.common.FabricLauncher;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceClassVisitor;

import static org.objectweb.asm.Opcodes.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ListIterator;
import java.util.function.Consumer;

public class EntrypointPatchPlatform extends EntrypointPatch {
	private static final String TARGET = "mindustry.core.Platform";
	private static final String TARGET_DESCRIPTOR = "Lmindustry/core/Platform;";

	public EntrypointPatchPlatform(EntrypointTransformer transformer) {
		super(transformer);
	}

	@Override
	public void process(FabricLauncher launcher, Consumer<ClassNode> classEmitter) {
		if (!classExists(launcher, TARGET)) {
			return;
		}
		try {
			boolean patched = false;
			final ClassNode node = loadClass(launcher, TARGET);
			if (node == null) {
				return;
			}
			final MethodNode methodNode = findMethod(node, method -> method.name.equals("loadJar"));
			if (methodNode == null) {
				return;
			}
			final ListIterator<AbstractInsnNode> it = methodNode.instructions.iterator();
			while (it.hasNext()) {
				AbstractInsnNode insn = it.next();
				if (insn instanceof MethodInsnNode) {
					if (((MethodInsnNode) insn).owner.equals("java/lang/ClassLoader") && ((MethodInsnNode) insn).name.equals("getSystemClassLoader")) {
						debug("Patching " + TARGET + " to support Java mods under Fabric");
						patched = true;
						it.set(new LdcInsnNode(Type.getType(TARGET_DESCRIPTOR)));
						it.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/Class", "getClassLoader", "()Ljava/lang/ClassLoader;"));
						break;
					}
				}
			}
			if (patched)
			classEmitter.accept(node);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}
