package org.mk0934.simulator.instructions;

import org.mk0934.simulator.Processor;
import org.mk0934.simulator.Status;

/**
 * Created by Maciej Kumorek on 10/28/2014.
 */
public class BranchGreaterEqualInstruction extends BranchInstruction {

    public BranchGreaterEqualInstruction(Integer args[], EncodedInstruction encodedInstruction) {
        super(Operand.BGE, args, encodedInstruction);
    }

    @Override
    public boolean shouldTakeBranch() {
        // Should take the branch?
        return this.statusRegisterValue == Status.EQ
                || this.statusRegisterValue == Status.GT;
    }
}
