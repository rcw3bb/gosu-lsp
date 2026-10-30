grammar GosuLanguage;

@lexer::members {
    /** True when the next non-whitespace character is '(' (i.e. get/set is a method name, not a property accessor). */
    private boolean nextNonWsIsLParen() {
        int i = 1;
        int c = _input.LA(i);
        while (c == ' ' || c == '\t' || c == '\r' || c == '\n') {
            c = _input.LA(++i);
        }
        return c == '(';
    }
}

// ============================================================
// Parser Rules
// ============================================================

compilationUnit
    : header topLevelDecl EOF
    | header EOF
    ;

header
    : packageDecl? usesDecl*
    ;

packageDecl
    : PACKAGE qualifiedName SEMI?
    ;

usesDecl
    : USES qualifiedName (DOT STAR)? SEMI?
    ;

topLevelDecl
    : modifiers
      ( classDecl
      | interfaceDecl
      | enumDecl
      | enhancementDecl
      | annotationDecl
      )
    ;

// ── Modifiers & annotations ──────────────────────────────────

modifiers
    : modifier*
    ;

modifier
    : annotation
    | PRIVATE | INTERNAL | PROTECTED | PUBLIC
    | STATIC | ABSTRACT | OVERRIDE | FINAL | TRANSIENT | REIFIED
    ;

annotation
    : AT qualifiedName typeVariableDefs? annotationArguments?
    ;

annotationArguments
    : LPAREN (argExpression (COMMA argExpression)*)? RPAREN
    ;

// ── Type declarations ────────────────────────────────────────

classDecl
    : CLASS Ident typeVariableDefs?
      (EXTENDS classOrInterfaceType)?
      (IMPLEMENTS classOrInterfaceType (COMMA classOrInterfaceType)*)?
      classBody
    ;

interfaceDecl
    : INTERFACE Ident typeVariableDefs?
      ((EXTENDS | IMPLEMENTS) classOrInterfaceType (COMMA classOrInterfaceType)*)?
      interfaceBody
    ;

enumDecl
    : ENUM Ident typeVariableDefs?
      (IMPLEMENTS classOrInterfaceType (COMMA classOrInterfaceType)*)?
      enumBody
    ;

enhancementDecl
    : ENHANCEMENT Ident typeVariableDefs? COLON classOrInterfaceType (LBRACK RBRACK)*
      enhancementBody
    ;

// 'annotation' is contextual (it also appears in package names such as java.lang.annotation)
annotationDecl
    : {_input.LT(1).getText().equals("annotation")}? Ident Ident typeVariableDefs? annotationBody
    ;

// Annotation elements may declare a default value: function sourceType() : String = ""
annotationBody
    : LBRACE annotationMember* RBRACE
    ;

annotationMember
    : modifiers
      ( functionDecl (EQ expression)?
      | propertyDecl
      | fieldDecl
      | classDecl
      | interfaceDecl
      | enumDecl
      ) SEMI?
    ;

classBody
    : LBRACE classMembers RBRACE
    ;

classMembers
    : declaration*
    ;

declaration
    : modifiers memberDecl SEMI?
    ;

memberDecl
    : functionDecl functionBody?
    | constructorDecl functionBody
    | propertyDecl functionBody?
    | fieldDecl
    | delegateDecl
    | classDecl
    | interfaceDecl
    | enumDecl
    ;

interfaceBody
    : LBRACE interfaceMember* RBRACE
    ;

interfaceMember
    : modifiers
      ( functionDecl
      | propertyDecl
      | fieldDecl
      | classDecl
      | interfaceDecl
      | enumDecl
      ) SEMI?
    ;

enhancementBody
    : LBRACE enhancementMember* RBRACE
    ;

enhancementMember
    : modifiers (functionDecl functionBody | propertyDecl functionBody) SEMI?
    ;

enumBody
    : LBRACE enumConstants? classMembers RBRACE
    ;

enumConstants
    : enumConstant (COMMA enumConstant)* COMMA? SEMI?
    ;

enumConstant
    : annotation* Ident optionalArguments
    ;

delegateDecl
    : DELEGATE Ident delegateStatement
    ;

delegateStatement
    : (COLON typeLiteral)? REPRESENTS typeLiteral (COMMA typeLiteral)* (EQ expression)?
    ;

// ── Member declarations ──────────────────────────────────────

typeVariableDefs
    : LT typeVariableDefinition (COMMA typeVariableDefinition)* GT
    ;

typeVariableDefinition
    : Ident (EXTENDS typeLiteralList)?
    ;

fieldDecl
    : VAR Ident optionalType (AS READONLY? Ident)? (EQ expression)?
    ;

propertyDecl
    : PROPERTY (GET | SET) Ident parameters (COLON typeLiteral)?
    ;

functionDecl
    : FUNCTION Ident typeVariableDefs? parameters (COLON typeLiteral)?
    ;

constructorDecl
    : CONSTRUCT parameters (COLON typeLiteral)?
    ;

functionBody
    : statementBlock
    ;

parameters
    : LPAREN parameterDeclarationList? RPAREN
    ;

parameterDeclarationList
    : parameterDeclaration (COMMA parameterDeclaration)*
    ;

parameterDeclaration
    : annotation* FINAL? Ident
      ( COLON typeLiteral (EQ expression)?
      | blockTypeLiteral
      | EQ expression
      )?
    ;

optionalType
    : COLON typeLiteral
    | blockTypeLiteral
    |
    ;

// ── Statements ───────────────────────────────────────────────

statementBlock
    : LBRACE statement* RBRACE
    ;

statement
    : ifStatement
    | tryCatchStatement
    | throwStatement
    | CONTINUE SEMI?
    | BREAK SEMI?
    | returnStatement
    | forEachStatement
    | whileStatement
    | doWhileStatement
    | switchStatement
    | usingStatement
    | assertStatement
    | FINAL localVarStatement SEMI?
    | localVarStatement SEMI?
    | expression SEMI?
    | statementBlock
    | SEMI
    ;

ifStatement
    : IF LPAREN expression RPAREN statement (ELSE statement)?
    ;

tryCatchStatement
    : TRY statementBlock
      ( catchClause+ (FINALLY statementBlock)?
      | FINALLY statementBlock
      )
    ;

catchClause
    : CATCH LPAREN VAR? Ident (COLON typeLiteral)? RPAREN statementBlock
    ;

assertStatement
    : ASSERT expression (COLON expression)? SEMI?
    ;

usingStatement
    : USING LPAREN (localVarStatement (COMMA localVarStatement)* | expression) RPAREN
      statementBlock (FINALLY statementBlock)?
    ;

returnStatement
    : RETURN expression? SEMI?
    ;

whileStatement
    : WHILE LPAREN expression RPAREN statement
    ;

doWhileStatement
    : DO statement WHILE LPAREN expression RPAREN SEMI?
    ;

switchStatement
    : SWITCH LPAREN expression RPAREN LBRACE switchGroup* RBRACE
    ;

switchGroup
    : (CASE expression COLON | DEFAULT COLON) statement*
    ;

throwStatement
    : THROW expression SEMI?
    ;

localVarStatement
    : VAR Ident optionalType (EQ expression)?
    ;

forEachStatement
    : (FOREACH | FOR) LPAREN forEachControl RPAREN statement
    ;

forEachControl
    : expression indexVar?
    | VAR? Ident IN expression indexRest?
    ;

indexRest
    : indexVar iteratorVar
    | iteratorVar indexVar
    | indexVar
    | iteratorVar
    ;

indexVar  : INDEX  Ident ;
iteratorVar : ITERATOR Ident ;

// ── Expressions ──────────────────────────────────────────────

expression
    : conditionalExpr (assignmentOp expression)?
    ;

conditionalExpr
    : ternaryExpr (QUESTION_COLON ternaryExpr)*
    ;

ternaryExpr
    : orExpr (QUESTION orExpr COLON orExpr)?
    ;

orExpr
    : andExpr (orOp andExpr)*
    ;

andExpr
    : bitwiseOrExpr (andOp bitwiseOrExpr)*
    ;

bitwiseOrExpr
    : bitwiseXorExpr (PIPE bitwiseXorExpr)*
    ;

bitwiseXorExpr
    : bitwiseAndExpr (CARET bitwiseAndExpr)*
    ;

bitwiseAndExpr
    : equalityExpr (AMP equalityExpr)*
    ;

equalityExpr
    : relationalExpr (equalityOp relationalExpr)*
    ;

relationalExpr
    : intervalExpr (relOp intervalExpr | TYPEIS typeLiteral)*
    ;

intervalExpr
    : shiftExpr (intervalOp shiftExpr)?
    ;

shiftExpr
    : additiveExpr (shiftOp additiveExpr)*
    ;

additiveExpr
    : multiplicativeExpr (additiveOp multiplicativeExpr)*
    ;

multiplicativeExpr
    : typeAsExpr (multOp typeAsExpr)*
    ;

typeAsExpr
    : unaryExpr ((TYPEAS | AS) typeLiteral)*
    ;

unaryExpr
    : (PLUS | MINUS) unaryExpr
    | TILDE unaryExpr
    | BANG unaryExpr
    | NOT unaryExpr
    | TYPEOF unaryExpr
    | STATICTYPEOF unaryExpr
    | PLUSPLUS postfixExpr
    | MINUSMINUS postfixExpr
    | postfixExpr
    ;

postfixExpr
    : primaryExpr (PLUSPLUS | MINUSMINUS)?
    ;

primaryExpr
    : atomExpr memberAccess*
    ;

atomExpr
    : newExpr
    | THIS
    | SUPER
    | literal
    | EVAL LPAREN expression RPAREN
    | Ident
    | LPAREN expression RPAREN
    | BACKSLASH blockExpr
    | standAloneInit
    ;

memberAccess
    : DOT Ident typeArguments? arguments?
    | QUESTION_DOT Ident typeArguments? arguments?
    | STAR_DOT Ident typeArguments? arguments?
    | HASH (Ident | CONSTRUCT) typeArguments? arguments?
    | LBRACK expression RBRACK
    | QUESTION_LBRACK expression RBRACK
    | arguments
    | PLUSPLUS
    | MINUSMINUS
    ;

newExpr
    : NEW classOrInterfaceType?
      ( arguments (LBRACE (initializer | classMembers) RBRACE)?
      | LBRACK RBRACK (LBRACK RBRACK)* arrayInitializer
      | LBRACK expression RBRACK (LBRACK expression? RBRACK)*
      )
    ;

blockExpr
    : parameterDeclarationList? ARROW (expression | statementBlock)
    ;

standAloneInit
    : LBRACE initializerExpression? RBRACE
    ;

initializer
    : initializerExpression
    | objectInitializer
    |
    ;

initializerExpression
    : mapInitializerList
    | arrayValueList
    ;

arrayValueList
    : expression (COMMA expression)*
    ;

mapInitializerList
    : expression ARROW expression (COMMA expression ARROW expression)*
    ;

objectInitializer
    : initializerAssignment (COMMA initializerAssignment)*
    ;

initializerAssignment
    : COLON Ident EQ expression
    ;

arrayInitializer
    : LBRACE (expression (COMMA expression)*)? RBRACE
    ;

arguments
    : LPAREN (argExpression (COMMA argExpression)*)? RPAREN
    ;

optionalArguments
    : arguments?
    ;

argExpression
    : COLON Ident EQ expression
    | expression
    ;

// ── Types ────────────────────────────────────────────────────

typeLiteral
    : type (AMP type)*
    ;

typeLiteralList
    : typeLiteral
    ;

type
    : classOrInterfaceType (LBRACK RBRACK)*
    | BLOCK blockLiteral
    ;

classOrInterfaceType
    : Ident typeArguments (DOT Ident typeArguments)*
    ;

typeArguments
    : LT typeArgument (COMMA typeArgument)* GT
    |
    ;

typeArgument
    : typeLiteral
    | QUESTION ((EXTENDS | SUPER) typeLiteral)?
    ;

blockTypeLiteral
    : blockLiteral
    ;

blockLiteral
    : LPAREN (blockLiteralArg (COMMA blockLiteralArg)*)? RPAREN (COLON typeLiteral)?
    ;

blockLiteralArg
    : Ident (EQ expression | blockTypeLiteral)
    | (Ident COLON)? typeLiteral (EQ expression)?
    ;

// ── Shared helpers ───────────────────────────────────────────

qualifiedName
    : Ident (DOT Ident)*
    ;

literal
    : NumberLiteral
    | StringLiteral
    | TRUE
    | FALSE
    | NULL_LIT
    ;

// ── Operator fragments ───────────────────────────────────────

orOp         : PIPE PIPE | OR ;
andOp        : AMP AMP | AND ;
equalityOp   : EQ EQ EQ | BANG EQ EQ | EQ EQ | BANG EQ | LT GT ;
relOp        : LT EQ | GT EQ | LT | GT ;
intervalOp   : DOT DOT | PIPE DOT DOT | DOT DOT PIPE | PIPE DOT DOT PIPE ;
shiftOp      : LT LT | GT GT GT | GT GT ;
additiveOp   : PLUS | MINUS | QUESTION PLUS | QUESTION MINUS ;
multOp       : STAR | SLASH | PERCENT | QUESTION STAR | QUESTION SLASH | QUESTION PERCENT ;
assignmentOp : EQ | PLUS_EQ | MINUS_EQ | STAR_EQ | SLASH_EQ | AMP_EQ | PIPE_EQ
             | CARET_EQ | PERCENT_EQ | LT LT EQ | GT GT GT EQ | GT GT EQ
             ;

// ============================================================
// Lexer Rules — Keywords (must precede Ident)
// ============================================================

PACKAGE      : 'package'     ;
USES         : 'uses'        ;
CLASS        : 'class'       ;
INTERFACE    : 'interface'   ;
ENUM         : 'enum'        ;
ENHANCEMENT  : 'enhancement' ;
EXTENDS      : 'extends'     ;
IMPLEMENTS   : 'implements'  ;
REPRESENTS   : 'represents'  ;
CONSTRUCT    : 'construct'   ;
FUNCTION     : 'function'    ;
PROPERTY     : 'property'    ;
VAR          : 'var'         ;
DELEGATE     : 'delegate'    ;
AS           : 'as'          ;
READONLY     : 'readonly'    ;
GET          : 'get' {!nextNonWsIsLParen()}? ;
SET          : 'set' {!nextNonWsIsLParen()}? ;
IF           : 'if'          ;
ELSE         : 'else'        ;
TRY          : 'try'         ;
CATCH        : 'catch'       ;
FINALLY      : 'finally'     ;
THROW        : 'throw'       ;
RETURN       : 'return'      ;
FOR          : 'for'         ;
FOREACH      : 'foreach'     ;
IN           : 'in'          ;
INDEX        : 'index'       ;
ITERATOR     : 'iterator'    ;
WHILE        : 'while'       ;
DO           : 'do'          ;
SWITCH       : 'switch'      ;
CASE         : 'case'        ;
DEFAULT      : 'default'     ;
BREAK        : 'break'       ;
CONTINUE     : 'continue'    ;
ASSERT       : 'assert'      ;
USING        : 'using'       ;
NEW          : 'new'         ;
THIS         : 'this'        ;
SUPER        : 'super'       ;
NULL_LIT     : 'null'        ;
TRUE         : 'true'        ;
FALSE        : 'false'       ;
BLOCK        : 'block'       ;
TYPEIS       : 'typeis'      ;
TYPEAS       : 'typeas'      ;
TYPEOF       : 'typeof'      ;
STATICTYPEOF : 'statictypeof';
OR           : 'or'          ;
AND          : 'and'         ;
NOT          : 'not'         ;
EVAL         : 'eval'        ;
ABSTRACT     : 'abstract'    ;
FINAL        : 'final'       ;
STATIC       : 'static'      ;
OVERRIDE     : 'override'    ;
TRANSIENT    : 'transient'   ;
REIFIED      : 'reified'     ;
PRIVATE      : 'private'     ;
INTERNAL     : 'internal'    ;
PROTECTED    : 'protected'   ;
PUBLIC       : 'public'      ;

// ── Identifiers ──────────────────────────────────────────────

Ident : Letter (Letter | Digit)* ;

// ── Literals ─────────────────────────────────────────────────

NumberLiteral
    : 'NaN'
    | 'Infinity'
    | ('0x' | '0X') HexDigit+ [lLsS]?
    | ('0b' | '0B') [01]+ IntSuffix?
    | '.' Digit+ Exp? FloatSuffix?
    | Digit+ ( '.' Digit* Exp? FloatSuffix?
             | Exp FloatSuffix?
             | FloatSuffix
             | IntSuffix
             | /* nothing */
             )
    ;

StringLiteral
    : '"'  ( EscapeSeq | ~["\\] )* '"'
    | '\'' ( EscapeSeq | ~['\\] )* '\''
    ;

// ── Punctuation & operators ──────────────────────────────────

LPAREN        : '('  ;
RPAREN        : ')'  ;
LBRACE        : '{'  ;
RBRACE        : '}'  ;
LBRACK        : '['  ;
RBRACK        : ']'  ;
SEMI          : ';'  ;
COLON         : ':'  ;
COMMA         : ','  ;
DOT           : '.'  ;
AT            : '@'  ;
HASH          : '#'  ;
BACKSLASH     : '\\' ;
ARROW         : '->' ;
QUESTION_COLON: '?:' ;
QUESTION_DOT  : '?.' ;
QUESTION_LBRACK: '?[';
STAR_DOT      : '*.' ;
LT            : '<'  ;
GT            : '>'  ;
EQ            : '='  ;
PLUS          : '+'  ;
MINUS         : '-'  ;
STAR          : '*'  ;
SLASH         : '/'  ;
PERCENT       : '%'  ;
AMP           : '&'  ;
PIPE          : '|'  ;
CARET         : '^'  ;
TILDE         : '~'  ;
BANG          : '!'  ;
QUESTION      : '?'  ;
PLUSPLUS      : '++' ;
MINUSMINUS    : '--' ;
PLUS_EQ       : '+=' ;
MINUS_EQ      : '-=' ;
STAR_EQ       : '*=' ;
SLASH_EQ      : '/=' ;
AMP_EQ        : '&=' ;
PIPE_EQ       : '|=' ;
CARET_EQ      : '^=' ;
PERCENT_EQ    : '%=' ;

// ── Whitespace & comments ────────────────────────────────────

WS           : [ \t\r\n]+      -> skip ;
BLOCK_COMMENT: '/*' .*? '*/'   -> skip ;
LINE_COMMENT : '//' ~[\r\n]*   -> skip ;

// ── Lexer fragments ──────────────────────────────────────────

fragment HexDigit  : [0-9a-fA-F] ;
fragment IntSuffix : [lLsS] | 'bi' | 'BI' | [bB] ;
fragment FloatSuffix: [fFdD] | 'bd' | 'BD' ;
fragment Exp       : [eE] [+\-]? Digit+ ;
fragment Digit     : [0-9] ;
fragment Letter    : [A-Za-z_$] ;
fragment EscapeSeq
    : '\\' [vabtfrn"'\\$<]
    | '\\u' HexDigit HexDigit HexDigit HexDigit
    | '\\' [0-3] [0-7] [0-7]
    | '\\' [0-7] [0-7]
    | '\\' [0-7]
    ;
