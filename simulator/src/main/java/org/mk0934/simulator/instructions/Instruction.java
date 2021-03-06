package org.mk0934.simulator.instructions;

import org.mk0934.simulator.Processor;

/**
 * Created by Maciej Kumorek on 10/16/2014.
 */
public abstract class Instruction {

    private String label;

    protected String encodedInstruction;

    public Instruction() {
        this.label = "";
    }

    public Instruction(String label) {
        this.label = parseLabel(label);
    }

    private final String parseLabel(String label) {

        return label.replace(':', '\0').trim();
    }

    /**
     * Parse string to get the operand
     * @param instructionString
     * @return
     */
    protected Operand parseOperand(String instructionString) {

        String string = instructionString.toLowerCase().trim();

        if (string.contains("add")) {
            return Operand.ADD;
        } else if(string.startsWith("sub ")) {
            return Operand.SUB;
        } else if(string.startsWith("mul ")) {
            return Operand.MUL;
        } else if(string.startsWith("mov ")) {
            return Operand.MOV;
        } else if(string.startsWith("ldm ")) {
            return Operand.LDM;
        } else if(string.startsWith("stm ")) {
            return Operand.STM;
        } else if(string.equals("nop")) {
            return Operand.NOP;
        } else if(string.startsWith("cmp ")) {
            return Operand.CMP;
        } else if(string.startsWith("bge ")) {
            return Operand.BGE;
        } else if(string.startsWith("bgt ")) {
            return Operand.BGT;
        } else if(string.startsWith("beq ")) {
            return Operand.BEQ;
        } else if(string.startsWith("jmp ")) {
            return Operand.JMP;
        } else if(string.startsWith("vldm ")) {
            return Operand.VLDM;
        } else if(string.startsWith("vstm ")) {
            return Operand.VSTM;
        } else if(string.startsWith("vadd ")) {
            return Operand.VADD;
        } else if(string.startsWith("vmul ")) {
            return Operand.VMUL;
        }

        throw new RuntimeException("Unknown operand in string " + string);
    }

    public String getLabel() {
        return this.label;
    }

    public final String getEncodedInstruction() {
        return this.encodedInstruction.trim();
    }

    public abstract DecodedInstruction decode(Processor processor);

    @Override
    public String toString() {
        return this.getEncodedInstruction();
    }
}
