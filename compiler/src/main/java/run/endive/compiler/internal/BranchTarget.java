package run.endive.compiler.internal;

import java.util.List;
import java.util.function.Function;
import run.endive.wasm.types.AnnotatedInstruction;
import run.endive.wasm.types.FunctionBody;
import run.endive.wasm.types.FunctionType;
import run.endive.wasm.types.Instruction;
import run.endive.wasm.types.OpCode;
import run.endive.wasm.types.ValType;

/**
 * A resolved branch target: its direction, the enclosing scope it targets, and that scope's block
 * type. Shared by ordinary-branch and try_table catch-handler unwinding in {@link WasmAnalyzer}.
 */
final class BranchTarget {

    final boolean forward;
    final Instruction scope;
    final FunctionType blockType;

    private BranchTarget(boolean forward, Instruction scope, FunctionType blockType) {
        this.forward = forward;
        this.scope = scope;
        this.blockType = blockType;
    }

    /** The values kept on top: block results (forward) or loop params (backward). */
    List<ValType> keepTypes() {
        return forward ? blockType.returns() : blockType.params();
    }

    /**
     * Resolves a branch to {@code label} from {@code fromIns}.
     *
     * @param body the function body containing the branch and its target
     * @param fromIns the branch instruction
     * @param label the target label, as an index into {@code body}'s instructions
     * @param functionType the enclosing function's type, used when the target is its implicit block
     * @param blockTypeOf maps a scope to its block type (i.e. {@code WasmAnalyzer::blockType}),
     *     which needs the module's type section
     */
    static BranchTarget resolve(
            FunctionBody body,
            AnnotatedInstruction fromIns,
            int label,
            FunctionType functionType,
            Function<Instruction, FunctionType> blockTypeOf) {
        boolean forward = true;
        var target = body.instructions().get(label);
        if (target.address() <= fromIns.address()) {
            target = body.instructions().get(label - 1);
            forward = false;
        }
        var scope = target.scope();
        if (scope.opcode() == OpCode.END) {
            // special scope for the function's implicit block
            return new BranchTarget(forward, TypeStack.FUNCTION_SCOPE, functionType);
        }
        return new BranchTarget(forward, scope, blockTypeOf.apply(scope));
    }
}
