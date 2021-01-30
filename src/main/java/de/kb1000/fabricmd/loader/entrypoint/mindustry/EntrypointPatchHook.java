package de.kb1000.fabricmd.loader.entrypoint.mindustry;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.entrypoint.EntrypointPatch;
import net.fabricmc.loader.entrypoint.EntrypointTransformer;
import net.fabricmc.loader.launch.common.FabricLauncher;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.io.IOException;
import java.util.ListIterator;
import java.util.function.Consumer;

import static org.objectweb.asm.Opcodes.*;

public class EntrypointPatchHook extends EntrypointPatch {
	public EntrypointPatchHook(EntrypointTransformer transformer) {
		super(transformer);
	}

	@Override
	public void process(FabricLauncher launcher, Consumer<ClassNode> classEmitter) {
		try {
			ClassNode classNode;
			MethodNode methodNode;
			if (launcher.getEnvironmentType() == EnvType.CLIENT) {
				classNode = loadClass(launcher, "mindustry.ClientLauncher");
				methodNode = findMethod(classNode, method -> method.name.equals("update") && method.desc.equals("()V"));
			} else {
				classNode = loadClass(launcher, "mindustry.server.ServerLauncher");
				methodNode = findMethod(classNode, method -> method.name.equals("init") && method.desc.equals("()V"));
			}

			boolean patched = false;

			ListIterator<AbstractInsnNode> it = methodNode.instructions.iterator();
			while (it.hasNext()) {
				AbstractInsnNode insn = it.next();
				if (insn instanceof FieldInsnNode) {
					FieldInsnNode fieldInsnNode = (FieldInsnNode) insn;
					if (fieldInsnNode.getOpcode() == GETSTATIC && fieldInsnNode.owner.equals("mindustry/Vars") && fieldInsnNode.name.equals("mods")) {
						final AbstractInsnNode nextInsnNode = fieldInsnNode.getNext();
						if (nextInsnNode instanceof InvokeDynamicInsnNode) {
							InvokeDynamicInsnNode invokeDynamicInsnNode = (InvokeDynamicInsnNode) nextInsnNode;
							if (invokeDynamicInsnNode.bsmArgs.length == 3 && invokeDynamicInsnNode.bsmArgs[1] instanceof Handle) {
								final Handle handle = ((Handle) invokeDynamicInsnNode.bsmArgs[1]);
								if (handle.getOwner().equals("mindustry/mod/Mod") && handle.getName().equals("init")) {
									debug("Found Vars.mods.eachClass(Mod::init); call, inserting entrypoint hook");
									it.previous();
									it.add(new FieldInsnNode(GETSTATIC, "mindustry/Vars", "dataDirectory", "Larc/files/Fi;"));
									it.add(new MethodInsnNode(INVOKEVIRTUAL, "arc/files/Fi", "file", "()Ljava/io/File;", false));
									it.add(new VarInsnNode(ALOAD, 0));
									it.add(new MethodInsnNode(INVOKESTATIC, "de/kb1000/fabricmd/loader/entrypoint/mindustry/hooks/Entrypoint" + (launcher.getEnvironmentType() == EnvType.CLIENT ? "Client" : "Server"), "start", "(Ljava/io/File;Ljava/lang/Object;)V", false));
									patched = true;
									break;
								}
							}
						}
					}
				}
			}

			if (!patched)
				throw new RuntimeException("Patching of " + classNode.name + "->" + methodNode.name + methodNode.desc + " not successful, could not find entrypoint insertion points!");
			classEmitter.accept(classNode);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}
