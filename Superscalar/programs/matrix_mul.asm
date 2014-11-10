; for( int i = 0; i < 10; i++ ) {
;   A[ i ] = B[ i ] + C[ i ];
; }
start:
    MOV r0, 0x0 ; int i =0
forloop:
    CMP r4, r0, 0xA
    BGE r4, end             ; loop or exit
    MUL r3, r0, 0x4		; align to next byte
    LDM r1, r3, arrayB	; load B[i]
    LDM r2, r3, arrayC	; load C[i]
    MUL r1, r1, r2		; B[i] * C[i]
    STM r1, r3, arrayA	; Store at A[i] => A[i] = B[i] * C[i]
    ADD r0, r0, 0x1		; i = i + 1
    JMP forloop			;
end:
    SVC 0
arrayA:
    0x0
    0x0
    0x0
    0x0
    0x0
    0x0
    0x0
    0x0
    0x0
    0x0
arrayB:
    0x2
    0x2
    0x2
    0x2
    0x2
    0x2
    0x2
    0x2
    0x2
    0x2
arrayC:
    0x1
    0x2
    0x3
    0x4
    0x5
    0x6
    0x7
    0x8
    0x9
    0xA