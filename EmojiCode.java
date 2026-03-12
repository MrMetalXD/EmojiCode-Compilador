//Librerias

import java.awt.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.*;
import javax.swing.table.DefaultTableModel;

//CLASE PRINCIPAL
public class EmojiCode {

    //MANEJO GLOBAL DE ERRORES
    static ErrorReporter reporter = new ErrorReporter();

    // TOKEN tipo
    //DEFINE EL VOCABULARIO DEL LENGUAJE EMOJILAND
    enum TokenType {
        Inicio, Fin,
        Imprimir, READ,
        Asignar,
        Suma, Resta, Multiplicacion, Divicion,
        // COMPARADORES
        Igual,
        Diferente,
        Mayor,
        Menor,
        MayorIgual,
        MenorIgual,
        // LÓGICOS
        AND,
        OR,
        NOT,
        IDENTIFIER, NUMBER,
        FinCodigo,
        IF, ELSE, WHILE, END_BLOCK,
        
        //Tipos de datos
        TIPO_INT,
        TIPO_STRING,
        TIPO_BOOL,
        TIPO_FLOAT,
        STRING_LITERAL,
        BOOL_TRUE,
        BOOL_FALSE
    }

    // TOKEN
    //Representa una unidad léxica:
    static class Token {

        TokenType type; //QUE ES
        String lexeme;  //QUE TEXTO O EMOJI LO ORIGINO
        int line;       //EN QUE LINEA APARECE

        //CONSTRUCTOR DE LA CLASE TOKEN
        Token(TokenType t, String l, int ln) {
            type = t;   //Asigna el tipo de token
            lexeme = l; //Guarda el lexema, es decir, el texto real que se leyo del codigo fuente
            line = ln;  //Guarda la linea donde se encontro el token
        }

        //mostrar el token como texto cuando se imprime
        public String toString() {
            return type + "  '" + lexeme + "'  (línea " + line + ")";
        }
    }

    //ERROR DE COMPILACION
    static class CompileError extends RuntimeException {

        String fase; //GUARDA EN QUE FASE DEL COMPILADOR OCURRIO EL ERROR
        int codigo;  //NUMERO PARA EL CODIGO DEL ERROR
        String titulo;  //DESCRIPCION CORTA DEL ERROR
        String detalle; //EXPLICACION DETALLADA DEL ERROR
        int linea;      //NUMERO DE LINEA DONDE OCURRIO EL ERROR

        //CONSTRUCTOR QUE CREA EL ERROR CON TODA SU INFORMACION
        CompileError(String f, int c, String t, String d, int l) {
            super(t);   //LLAMA AL CONSTRUCTOR DEL RUNTIME EXCEPCTION
            fase = f;
            codigo = c;
            titulo = t;
            detalle = d;
            linea = l;
        }

        //FORMATO DEL ERROR
        @Override
        public String toString() {
            return "❌ [" + fase + "] Error " + codigo + ": " + titulo
                    + "\n   → " + detalle
                    + "\n   → Línea " + linea + "\n";
        }
    }

    //RECOLECTOR DE ERRORES
    static class ErrorReporter {

        //CREA UNA LISTA DONDE SE VAN A GUARDAR LOS ERRORES
        List<CompileError> errores = new ArrayList<>();

        //METODO PARA REGISTRAR UN NUEVO ERROR
        void add(CompileError e) {
            errores.add(e); //AGREGA EL NUEVO ERROR A LA LISTA
        }

        //VERIFICA SI HAY ERRORES REGISTRADOS
        boolean hasErrors() {
            return !errores.isEmpty();//DEVUELVE VERDADERO SI HAY 1 O MAS ERRORES
        }

        //MUESTRA LOS ERRORES EN PANTALLA
        void print(JTextArea area) {
            for (CompileError e : errores) { //RECORRE TODOS LOS ERRORES ALMACENADOS
                area.append(e.toString());  //AGREGA EL TEXTO DEL ERROR AL AREA DE TEXTO
            }
        }
    }

        //CREAR NUMEROS DE LINEA
        static class LineNumberArea extends JComponent implements DocumentListener {

            private final JTextArea editor;
            private static final int PADDING = 5;

            LineNumberArea(JTextArea editor) {
                this.editor = editor;
                setBackground(Color.BLACK);
                setForeground(Color.GREEN);
                setFont(editor.getFont());
                editor.getDocument().addDocumentListener(this);
            }

            @Override
            public void insertUpdate(DocumentEvent e)  { repaint(); updateWidth(); }
            @Override
            public void removeUpdate(DocumentEvent e)  { repaint(); updateWidth(); }
            @Override
            public void changedUpdate(DocumentEvent e) { repaint(); updateWidth(); }

            private void updateWidth() {
                int lines = editor.getLineCount();
                int digits = String.valueOf(lines).length();
                FontMetrics fm = getFontMetrics(getFont());
                int w = fm.charWidth('0') * digits + PADDING * 2;
                setPreferredSize(new Dimension(w, getPreferredSize().height));
                revalidate();
            }

            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.setColor(Color.BLACK);
                g.fillRect(0, 0, getWidth(), getHeight());

                g.setColor(Color.GREEN);
                g.setFont(editor.getFont());

                FontMetrics fm = g.getFontMetrics();

                // Usar la posición Y real de cada línea desde el editor
                int lineCount = editor.getLineCount();
                String text = editor.getText();
                if (text.endsWith("\n") || text.isEmpty()) lineCount = Math.max(1, lineCount - 1);

                int digits = String.valueOf(lineCount).length();
                int w = fm.charWidth('0') * digits + PADDING;

                int totalHeight = 0;
                for (int i = 0; i < lineCount; i++) {
                    try {
                        Rectangle r = editor.modelToView(
                            editor.getDocument().getDefaultRootElement()
                                .getElement(i).getStartOffset()
                        );
                        if (r == null) continue;
                        String num = String.valueOf(i + 1);
                        int x = w - fm.stringWidth(num);
                        int y = r.y + fm.getAscent();
                        g.drawString(num, x, y);
                        totalHeight = r.y + r.height;
                    } catch (Exception ex) {
                        // ignorar
                    }
                }

                setPreferredSize(new Dimension(w + PADDING, totalHeight));
            }
        }


    //LEXER (ANALISIS LEXICO)
    static class Lexer {

        List<Token> tokens = new ArrayList<>();
        private final String src;   //CODIGO FUENTE COMPLETO A ANALIZAR
        private int pos = 0;
        private int line = 1;

        Lexer(String s) {
            src = (s == null) ? "" : s;
        }
        
        void scanString() {
            pos++; // saltar la "
            int start = pos;
            
            while (pos < src.length()) {

                int c = src.codePointAt(pos);

                if (c == '"') {
                    break;
                }

                if (c == '\n') {
                    line++;
                }

                pos += Character.charCount(c);
            }

            if (pos >= src.length()) {
                reporter.add(new CompileError(
                        "Léxico", 21,
                        "Cadena no cerrada",
                        "Falta cerrar la cadena con '\"'",
                        line
                ));
                return;
            }
            String value = src.substring(start, pos);
            tokens.add(new Token(TokenType.STRING_LITERAL, value, line));
            pos++; // saltar la " de cierre
        }

        //METODO QUE RECORRE EL CODIGO FUENTE CARACTER POR CARACTER
        void scan() {

            while (pos < src.length()) {
                int c = src.codePointAt(pos);

                if (c == '\n') {
                    line++;
                    pos += Character.charCount(c);
                    continue;
                }

                if (Character.isWhitespace(c)) {
                    pos += Character.charCount(c);
                    continue;
                }

                //COMENTARIOS 💬
                if (src.startsWith("💬", pos)) {

                    while (pos < src.length()) {

                        int c2 = src.codePointAt(pos);

                        if (c2 == '\n') {
                            break;
                        }

                        pos += Character.charCount(c2);
                    }

                    continue;
                }
                // NÚMERO
                if (Character.isDigit(c)) {
                    scanNumber();
                    continue;
                }

                // STRING — DEBE ESTAR ANTES DEL ERROR FINAL
                if (c == '"') {
                    scanString();
                    continue;
                }

                // INICIO / FIN
                if (matchEmoji("🚀", TokenType.Inicio)) {
                    continue;
                }
                if (matchEmoji("🛑", TokenType.Fin)) {
                    continue;
                }

                // ENTRADA / SALIDA
                if (matchEmoji("📢", TokenType.Imprimir)) {
                    continue;
                }
                if (matchEmoji("📝", TokenType.READ)) {
                    continue;
                }

                // CONTROL
                if (matchEmoji("🥺", TokenType.IF)) {
                    continue;
                }
                if (matchEmoji("😉", TokenType.ELSE)) {
                    continue;
                }
                if (matchEmoji("🔄️", TokenType.WHILE)) {
                    continue;
                }
                if (matchEmoji("🔄", TokenType.WHILE)) {
                    continue;
                }
                if (matchEmoji("🔚", TokenType.END_BLOCK)) {
                    continue;
                }

                // OPERADORES
                if (matchEmoji("➕", TokenType.Suma)) {
                    continue;
                }
                if (matchEmoji("➖", TokenType.Resta)) {
                    continue;
                }
                if (matchEmoji("✖️", TokenType.Multiplicacion)) {
                    continue;
                }
                if (matchEmoji("✖", TokenType.Multiplicacion)) {
                    continue;
                }
                if (matchEmoji("➗", TokenType.Divicion)) {
                    continue;
                }

                // ASIGNACIÓN
                if (matchEmoji("🔧", TokenType.Asignar)) {
                    continue;
                }
                // COMPARADORES
                if (matchEmoji("⚖️", TokenType.Igual)) {
                    continue;
                }
                if (matchEmoji("🚫", TokenType.Diferente)) {
                    continue;
                }
                if (matchEmoji("🔼", TokenType.Mayor)) {
                    continue;
                }
                if (matchEmoji("🔽", TokenType.Menor)) {
                    continue;
                }
                if (matchEmoji("⏫", TokenType.MayorIgual)) {
                    continue;
                }
                if (matchEmoji("⏬", TokenType.MenorIgual)) {
                    continue;
                }

                // OPERADORES LÓGICOS
                if (matchEmoji("🤝", TokenType.AND)) {
                    continue;
                }
                if (matchEmoji("🔀", TokenType.OR)) {
                    continue;
                }
                if (matchEmoji("❗", TokenType.NOT)) {
                    continue;
                }
                // IDENTIFICADOR :x:
                if (c == ':') {
                    scanIdentifier();
                    continue;
                }
                // TIPOS DE DATOS
                if (matchEmoji("🔢", TokenType.TIPO_INT))
                    continue;
                if (matchEmoji("🌊", TokenType.TIPO_FLOAT)) {
                    continue;
                }
                if (matchEmoji("🔤", TokenType.TIPO_STRING)) {
                    continue;
                }
                if (matchEmoji("☑️", TokenType.TIPO_BOOL)) continue;  // con variation selector
                if (matchEmoji("☑",  TokenType.TIPO_BOOL)) continue;
                

                // ERROR
                reporter.add(new CompileError(
                        "Léxico",
                        3,
                        "Símbolo no válido",
                        "Emoji no reconocido",
                        line
                ));
                pos += Character.charCount(c);

            }

            tokens.add(new Token(TokenType.FinCodigo, "", line));
        }

        // METODOS AUXILIARES
        boolean matchEmoji(String emoji, TokenType type) {
            if (src.startsWith(emoji, pos)) {   //VERIFICA SI EL EMOJI COINCIDE EN LA POSICIÓN ACTUAL
                tokens.add(new Token(type, emoji, line));
                pos += emoji.length();
                return true;
            }
            return false;
        }

        //ANALIZA QUE LAS VARIBALES LLEVEN EL FORMATO :x:
        void scanIdentifier() {

            int start = pos + 1;
            int end = src.indexOf(':', start);

            if (end == -1) {
                reporter.add(new CompileError(
                        "Léxico",
                        8,
                        "Dos puntos faltantes en variable",
                        "Se esperaba ':' para cerrar el identificador",
                        line
                ));
                pos++;
                return;
            }

            if (start == end) {
                reporter.add(new CompileError(
                        "Léxico",
                        1,
                        "Variable vacía",
                        "La variable debe tener el formato :x:",
                        line
                ));

                // consumir ambos dos puntos :: completamente
                pos = end + 1;
                return;
            }

            String name = src.substring(start, end);

            if (!name.matches("[a-zA-Z][a-zA-Z0-9]*")) {
                reporter.add(new CompileError(
                        "Léxico",
                        1,
                        "Variable no válida",
                        "Nombre inválido: '" + name + "'",
                        line
                ));
                pos = end + 1;
                return;
            }

            tokens.add(new Token(TokenType.IDENTIFIER, name, line));
            pos = end + 1;
        }
        

        void scanNumber() {

            int start = pos;

            while (pos < src.length()) {

                int c = src.codePointAt(pos);

                if (!Character.isDigit(c)) {
                    break;
                }

                pos += Character.charCount(c);
            }
            
            // DECIMAL
            if (pos < src.length() && src.codePointAt(pos) == '.'
                    && pos + 1 < src.length()
                    && Character.isDigit(src.codePointAt(pos + 1))) {

                pos++;

                while (pos < src.length()) {

                    int c = src.codePointAt(pos);

                    if (!Character.isDigit(c)) {
                        break;
                    }

                    pos += Character.charCount(c);
                }
            }

            tokens.add(new Token(
                    TokenType.NUMBER,
                    src.substring(start, pos),
                    line
            ));
        }

    } //FIN DEL LEXER?/-----------------------------------

    // LINE NUMBER VIEW
    // VENTANA DE AYUDA DE ERRORES
    static class ErrorHelpWindow extends JFrame {

        //CREA UNA VENTANA AYUDA DE ERRORES
        ErrorHelpWindow() {
            setTitle("Ayuda de errores - EmojiLang");
            setSize(520, 420);
            setLocationRelativeTo(null);

            //LE DA EL CONTENIDO A LA VENTANA
            JTextArea t = new JTextArea();
            t.setEditable(false);
            t.setText(
                    "ERRORES COMUNES EN EMOJILANG\n\n"
                    + "❌ Error 1: Variable no válida \n"
                    + "   ->La variable que estás declarando no es válida o no cumple con las reglas de nuestro compilador. \n"
                    + "❌ Error 2: Símbolo no reconocido\n"
                    + "   ->Se ha detectado un símbolo o carácter que no pertenece al lenguaje. \n"
                    + "❌ Error 3: Emoji no válido\n"
                    + "   ->El emoji utilizado no forma parte del conjunto de emojis definidos en el lenguaje.\n"
                    + "❌ Error 4: Inicio de programa no encontrado\n"
                    + "   ->El programa no contiene el emoji de inicio obligatorio 🚀.\n"
                    + "❌ Error 5: Fin de programa no encontrado\n"
                    + "   ->El programa no contiene el emoji de fin obligatorio 🛑.\n"
                    + "❌ Error 6: Orden incorrecto de instrucciones\n"
                    + "   ->Las instrucciones no se encuentran en el orden correcto dentro del programa.\n"
                    + "❌ Error 7: Asignación mal formada\n"
                    + "   ->La instrucción de asignación no cumple con la estructura correcta.\n"
                    + "❌ Error 7: Asignación mal formada\n"
                    + "   ->La instrucción de asignación no cumple con la estructura correcta.\n"
                    + "❌ Error 8: Dos puntos faltantes en variable\n"
                    + "   ->Se esperaba el símbolo : para abrir o cerrar el nombre de la variable.\n"
                    + "❌ Error 9: Variable no declarada\n"
                    + "   ->Se intenta utilizar una variable que no ha sido declarada previamente.\n"
                    + "❌ Error 10: Valor no numérico\n"
                    + "   ->Se esperaba un valor numérico y se recibió otro tipo de dato.\n"
                    + "❌ Error 11: Operación incompleta\n"
                    + "   ->La operación aritmética no contiene todos los operandos necesarios.\n"
                    + "❌ Error 12: División entre cero\n"
                    + "   ->Se intentó realizar una división entre cero durante la ejecución.\n"
                    + "❌ Error 13: Lectura sin variable\n"
                    + "   ->La instrucción de lectura 📝 no especifica una variable destino.\n"
                    + "❌ Error 14: Impresión sin argumento\n"
                    + "   ->La instrucción de impresión 📢 no contiene ningún valor o variable a imprimir.\n"
                    + "❌ Error 15: Impresión sin argumento\n"
                    + "   ->La instrucción de impresión 📢 no contiene ningún valor o variable a imprimir.\n"
                    + "❌ Error 16: Paréntesis desbalanceados\n"
                    + "   ->Los paréntesis de una expresión no están balanceados correctamente.\n"
                    + "❌ Error 17: Variable redeclarada\n"
                    + "   ->La variable ya fue declarada anteriormente.\n"
                    + "❌ Error 18: Token inesperado\n"
                    + "   ->Se encontró un token que no se esperaba según la gramática del lenguaje.\n"
                    + "❌ Error 19: Bucle infinito detectado\n"
                    + "   ->El ciclo WHILE no modifica su condición y nunca termina.\n"
                    + "❌ Error 20: No se cerro el bloque\n"
                    + "   ->El bloque nunca fue cerrado.\n"
            );
            add(new JScrollPane(t)); //COLOCA EL JTEXTAREA EN UN SCROLL
        }
    }

// PANEL DE AYUDA DE EMOJIS 
    static class EmojiHelpPanel extends JPanel { //DEFINE UN COMPONENTE GRAFICO QUE HEREDA DEL JPANEL

        //CONSTUCTOR
        EmojiHelpPanel() {
            setLayout(new BorderLayout());
            setPreferredSize(new Dimension(260, 100));
            setBackground(new Color(245, 245, 245));

            //AREA DE TEXTO DONDE SE ENCUENTRAN LOS EMOJIS
            JTextArea help = new JTextArea();
            help.setEditable(false);
            help.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 14)); //FUENTE QUE SOPORTA EL USO DE EMOJIS
            help.setBorder(new EmptyBorder(10, 10, 10, 10));

            help.setText(
                    "📘 Lista de emojis para utilizar :333\n\n"
                    + "🚀 Inicio del programa\n"
                    + "🛑 Fin del programa\n\n"
                    + "📢 Imprimir valor\n"
                    + "📝 Leer valor\n\n"
                    + "🔧 Asignación\n"
                    + "   Ejemplo: 🔧 :x: :x: ➕ 1\n\n"
                    + "🥺 IF\n"
                    + "😉 ELSE\n"
                    + "🔄 WHILE\n"
                    + "🔚 END_BLOCK\n"
                    + "➕ Suma\n"
                    + "➖ Resta\n"
                    + "✖️ Multiplicación\n"
                    + "➗ División\n\n"
                    + "💬 Comentario\n"
                    + "⚖️ Igual\n"
                    + "🚫 Diferente\n"
                    + "🔼 Mayor\n"
                    + "🔽 Menor\n"
                    + "⏫ Mayor o igual\n"
                    + "⏬ Menor o igual\n\n"
                    + "🤝 AND lógico\n"
                    + "🔀 OR lógico\n"
                    + "❗ NOT lógico\n\n"
                    + "   Todo después del emoji es ignorado\n\n"
                    + " TIPOS DE DATOS \n"
                    + "🔢 :x: 10        Entero\n"
                    + "🌊 :x: 3.14      Decimal\n"
                    + "🔤 :x: \"Hola\"    Texto\n"
                    + "☑️ :x: ✅         Booleano\n\n"
                    + "── BOOLEANOS ──\n"
                    + "✅ verdadero\n"
                    + "❎ falso\n\n"
            );

            add(new JScrollPane(help), BorderLayout.CENTER); //AÑADE EL SCROLL SI EL CONTENIDO NO CABE
        }
    }

    //ARBOL SINTACTICO (AST)
    static abstract class Stmt { //CLASE ABSTRACTA PARA MANEJAR LAS INSTRUCCIONES DE MANERA UNIFORME

        int line;
    }
    
    static class StmtDeclare extends Stmt {
        String tipo;   // "INT", "FLOAT", "STRING", "BOOL"
        String name;
        Expr expr;

        StmtDeclare(String tipo, String name, Expr expr, int ln) {
            this.tipo = tipo;
            this.name = name;
            this.expr = expr;
            this.line = ln;
        }
    }

    static class StmtPrint extends Stmt { //REPRESENTA UNA INSTRUCCION DE IMPRESION

        Expr value; //EXPRESION QUE SE VA A IMPRIMIR

        StmtPrint(Expr v, int ln) { //GUARDA EL VALOR A IMPRIMIR
            value = v;
            line = ln;
        }
    }

    //REPRESENTA UNA INSTRUCCION DE LECTURA
    static class StmtRead extends Stmt {

        String name;

        //ASIGAN EL NOMBRE DE LA VARIABLE
        StmtRead(String n, int ln) {
            name = n;
            line = ln;
        }

    }

    //REPRESENTA UNA INSTRUCCION DE ASIGACNION
    static class StmtAssign extends Stmt {

        String name;    //VARIABLE DESTINO
        Expr expr;      //EXPRESION A EVALUAR

        //CONSTRUYE LA ASIGNACION
        StmtAssign(String n, Expr e, int ln) {
            name = n;
            expr = e;
            line = ln;
        }
    }

    //REPRESENTA UNA ESTRUCTURA CONDICIONAL IF/ELSE
    static class StmtIf extends Stmt {

        Expr condition;         //CONDICION A EVALUAR
        List<Stmt> thenBranch;  //INSTRUCCIONES SI LA CONDICIÓN ES VERDADERA
        List<Stmt> elseBranch;  //INSTRUCCIONES SI LA CONDICION ES FASA

        //CONSTRUYE LA ESTRUCTURA CONDICIONAL
        StmtIf(Expr c, List<Stmt> t, List<Stmt> e, int ln) {
            condition = c;
            thenBranch = t;
            elseBranch = e;
            line = ln;
        }
    }

    static class StmtWhile extends Stmt { //REPRESENTA UN CICLO WHILE

        Expr condition; //CONDICIONES DEL CICLO
        List<Stmt> body;//INSTUCCIONES QUE SE REPITEN

        //CONSTRUYE EL CICLO
        StmtWhile(Expr c, List<Stmt> b, int ln) {
            condition = c;
            body = b;
            line = ln;
        }
    }

//EXPRESIONES
    //CLASE BASE
    static abstract class Expr {

        int line;
    }

    static class ExprNumber extends Expr { //REPRESENTA UN NUMERO LITERAL

        double value;   //NUMERO

        //GUARDA EL NUMERO
        ExprNumber(double v, int ln) {
            value = v;
            line = ln;
        }
    }

    static class ExprVar extends Expr { //REPRESENTA EL USO DE UNA VARIABLE

        String name;    //NOMBRE

        //GUARDA EL NOMBRE
        ExprVar(String n, int ln) {
            name = n;
            line = ln;
        }
    }

    static class ExprBinary extends Expr {  //REPRESENTA UNA OPERACION BINARIA

        Expr left;  //OPERANDO IZQ
        String op;  //OPERADOR
        Expr right; //OPERADOR DERECHO

        ExprBinary(Expr l, String o, Expr r, int ln) {  //CONSTUYE LA EXPRESION BINARIA
            left = l;
            op = o;
            right = r;
            line = ln;
        }
    }
    
    static class ExprString extends Expr {
        String value;
        ExprString(String v, int ln) {
            value = v;
            line = ln;
        }
    }

    static class ExprBool extends Expr {
        boolean value;
        ExprBool(boolean v, int ln) {
            value = v;
            line = ln;
        }
    }

// ANALIZADOR (ANALISIS SINTACTICO)
    static class Parser {

        private final List<Token> tokens;
        private int pos = 0;

        //RECIBE EL LEXER Y TOMA SU LISTA DE TOKENS
        Parser(Lexer lexer) {
            this.tokens = lexer.tokens;
        }

        void synchronize() {
            int steps = 0;
            while (peek() != null && !isSync(peek())) {
                advance();
                
                if(++steps > 500){
                    reporter.add(new CompileError(
                    "Sintáctico", 18,
                    "Error de recuperación",
                    "No se pudo recuperar del error sintáctico",
                    peek() != null ? peek().line : -1
                    ));
                    break;
                }
            }
        }

        //MIRA EL TOKEN ACTUAL SIN AVANZAR
        Token peek() {
            if (pos >= tokens.size()) {
                return null;
            }
            return tokens.get(pos);
        }

        //DEVUELVE EL TOKEN ACTUAL Y AVANZA LA POSICION
        Token advance() {
            return tokens.get(pos++);
        }

        //VERIFICA SI LOS TOKEN ACTUALES SON DEL TIPO ESPERADO
        boolean match(TokenType t) {
            if (peek() != null && peek().type == t) {
                advance();
                return true;
            }
            return false;
        }

        void expect(TokenType t, String msg) {
            if (!match(t)) {
                reporter.add(new CompileError(
                        "Sintáctico",
                        4,
                        "Error de sintaxis",
                        msg,
                        peek() != null ? peek().line : -1
                ));
                synchronize();
            }
        }

        boolean isSync(Token t) {
            return t.type == TokenType.Imprimir
                    || t.type == TokenType.Asignar
                    || t.type == TokenType.IF
                    || t.type == TokenType.WHILE
                    || t.type == TokenType.Fin
                    || t.type == TokenType.END_BLOCK;
        }

        // PROGRAMA
        List<Stmt> parseProgram() {

            List<Stmt> program = new ArrayList<>();

            // 🚀 Inicio obligatorio
            expect(TokenType.Inicio,
                    "❌ Error 4: Inicio de programa no encontrado 🚀");

            // instrucciones
            while (peek() != null && peek().type != TokenType.Fin) {

                if (peek().type == TokenType.END_BLOCK) {
                    reporter.add(new CompileError(
                            "Sintáctico",
                            18,
                            "Token inesperado",
                            "🔚 no puede aparecer aquí",
                            peek().line
                    ));
                    advance();
                    continue;
                }
                
                if (peek().type == TokenType.FinCodigo) break;
                
                Stmt s = parseStmt();
                if (s !=null) program.add(s);
            }
            

            // 🛑 Fin obligatorio
            expect(TokenType.Fin,
                    "❌ Error 5: Fin de programa no encontrado 🛑");

            // 🔥🔥🔥 AQUÍ ESTÁ LA CLAVE 🔥🔥🔥
            // ❌ NO puede haber código después del 🛑
            if (peek() != null && peek().type != TokenType.FinCodigo) {
                reporter.add(new CompileError(
                        "Sintáctico",
                        6,
                        "Código después del fin del programa",
                        "No se permite escribir instrucciones después de 🛑",
                        peek().line
                ));
            }

            return program; //RETORNA
        }

        // DECLARACIONES
        Stmt parseStmt() {

            // PRINT 📢
            if (match(TokenType.Imprimir)) {
                int ln = tokens.get(pos - 1).line;
                Expr e = parseExpr();
                return new StmtPrint(e, ln);
            }

            // READ 📝
            if (match(TokenType.READ)) {
                if (peek().type != TokenType.IDENTIFIER) {
                    reporter.add(new CompileError(
                            "Sintáctico", 13,
                            "Lectura sin variable",
                            "📝 requiere una variable",
                            peek().line
                    ));
                    synchronize();
                    return null;
                }
                Token id = advance();
                return new StmtRead(id.lexeme, id.line);
            }
            
            if (match(TokenType.TIPO_INT) || match(TokenType.TIPO_FLOAT)
                    || match(TokenType.TIPO_STRING) || match(TokenType.TIPO_BOOL)) {

                Token tipoTok = tokens.get(pos - 1);
                String tipo = switch (tipoTok.type) {
                    case TIPO_INT -> "INT";
                    case TIPO_FLOAT -> "FLOAT";
                    case TIPO_STRING -> "STRING";
                    case TIPO_BOOL -> "BOOL";
                    default -> "UNKNOWN";
                };

                if (peek() == null || peek().type != TokenType.IDENTIFIER) {
                    reporter.add(new CompileError("Sintáctico", 7,
                            "Declaración mal formada", "Falta nombre de variable", tipoTok.line));
                    synchronize();
                    return null;
                }

                Token id = advance();
                Expr expr = parseExpr();
                return new StmtDeclare(tipo, id.lexeme, expr, id.line);
            }

            // ASIGNACIÓN 🔧
            if (match(TokenType.Asignar)) {

                if (peek().type != TokenType.IDENTIFIER) {
                    reporter.add(new CompileError(
                            "Sintáctico", 7,
                            "Asignación mal formada",
                            "Falta variable",
                            peek().line
                    ));
                    synchronize();
                    return null;
                }

                Token id = advance();
                Expr expr = parseExpr();
                return new StmtAssign(id.lexeme, expr, id.line); //GUARDA LA LINEA DE LA ASIGNACION PARA ERRORES SEMANTICOS FUTUROS
            }

            // IF 🥺
            if (match(TokenType.IF)) {
                int ln = tokens.get(pos - 1).line;
                Expr condition = parseExpr();
                List<Stmt> thenBranch = new ArrayList<>();
                List<Stmt> elseBranch = new ArrayList<>();

                // THEN
                while (peek() != null
                        && peek().type != TokenType.ELSE
                        && peek().type != TokenType.END_BLOCK
                        && peek().type != TokenType.Fin) {

                    thenBranch.add(parseStmt());
                }

                // ELSE 😉
                if (match(TokenType.ELSE)) {

                    while (peek() != null
                            && peek().type != TokenType.END_BLOCK
                            && peek().type != TokenType.Fin) {

                        elseBranch.add(parseStmt());
                    }
                }

                if (!match(TokenType.END_BLOCK)) {

                    reporter.add(new CompileError(
                            "Sintáctico",
                            20,
                            "Bloque no cerrado",
                            "Falta 🔚 para cerrar el IF",
                            peek() != null ? peek().line : -1
                    ));

                    // recuperación
                    if (peek() != null && peek().type == TokenType.Fin) {
                        return new StmtIf(condition, thenBranch, elseBranch, ln);
                    }

                    synchronize();
                }
                return new StmtIf(condition, thenBranch, elseBranch, ln);
            }

            // WHILE 🔄
            if (match(TokenType.WHILE)) {
                int ln = tokens.get(pos - 1).line;
                Expr condition = parseExpr();
                List<Stmt> body = new ArrayList<>();

                while (peek() != null
                        && peek().type != TokenType.END_BLOCK
                        && peek().type != TokenType.Fin) {

                    body.add(parseStmt());
                }

                if (!match(TokenType.END_BLOCK)) {

                    reporter.add(new CompileError(
                            "Sintáctico",
                            20,
                            "Bloque no cerrado",
                            "Falta 🔚 para cerrar el WHILE",
                            peek() != null ? peek().line : -1
                    ));

                    if (peek() != null && peek().type == TokenType.Fin) {
                        return new StmtWhile(condition, body, ln);
                    }

                    synchronize();
                }

                return new StmtWhile(condition, body, ln);
            }

            // ERROR
            reporter.add(new CompileError(
                    "Sintáctico",
                    18,
                    "Token inesperado",
                    "No se esperaba '" + peek().lexeme + "'",
                    peek().line
            ));
            synchronize();
            return null;
        }

        // EXPRESIONES
        Expr parseExpr() {

            Expr expr = parseTerm();
            
            if (expr == null) return null;

            while (peek() != null
                    && (peek().type == TokenType.Suma
                    || peek().type == TokenType.Resta
                    || peek().type == TokenType.Igual
                    || peek().type == TokenType.Diferente
                    || peek().type == TokenType.Mayor
                    || peek().type == TokenType.Menor
                    || peek().type == TokenType.MayorIgual
                    || peek().type == TokenType.MenorIgual
                    ||peek().type == TokenType.AND      // ← AGREGAR
                     ||peek().type == TokenType.OR)) {

                Token op = advance();

                Expr right = parseTerm();
                
                if(right == null) return expr;

                expr = new ExprBinary(expr, op.lexeme, right, op.line);
            }

            return expr;
        }

        Expr parseTerm() {
            Expr left = parseFactor();

            if (left == null) {
                return null;
            }

            while (peek() != null
                    && (peek().type == TokenType.Multiplicacion
                    || peek().type == TokenType.Divicion)) {

                Token opToken = advance();
                Expr right = parseFactor();

                if (right == null) {
                    return null;
                }

                left = new ExprBinary(left, opToken.lexeme, right, opToken.line);
            }
            return left;
        }

        Expr parseFactor() {

            if (peek() == null || peek().type == TokenType.FinCodigo) {
                reporter.add(new CompileError(
                        "Sintáctico",
                        11,
                        "Expresión incompleta",
                        "Se esperaba un número o variable",
                       -1
                ));
                return null;
            }
            // NO consumir tokens de control como si fueran expresiones
            TokenType tp = peek().type;
            if (tp == TokenType.END_BLOCK || tp == TokenType.ELSE
                    || tp == TokenType.Fin || tp == TokenType.IF
                    || tp == TokenType.WHILE || tp == TokenType.Imprimir
                    || tp == TokenType.Asignar || tp == TokenType.READ) {
                reporter.add(new CompileError(
                        "Sintáctico", 11,
                        "Expresión incompleta",
                        "Se esperaba un número o variable antes de '" + peek().lexeme + "'",
                        peek().line
                ));
                return null;
            }
    
            Token t = advance();

            if (t.type == TokenType.NUMBER) {
                return new ExprNumber(Double.parseDouble(t.lexeme), t.line);

            }
            if (t.type == TokenType.IDENTIFIER) {
                return new ExprVar(t.lexeme, t.line);

            }
            if (t.type == TokenType.STRING_LITERAL) {
                return new ExprString(t.lexeme, t.line);
            }
            if (t.type == TokenType.BOOL_TRUE) {
                return new ExprBool(true, t.line);
            }
            if (t.type == TokenType.BOOL_FALSE) {
                return new ExprBool(false, t.line);
            }

            reporter.add(new CompileError(
                    "Sintáctico",
                    11,
                    "Expresión inválida",
                    "Token inesperado '" + t.lexeme + "'",
                    t.line
            ));
            return null;
        }
    }

    //ANALIZADOR SEMANTICO
    static class SemanticAnalyzer {
        // CLASE INTERNA QUE SE USA PARA GUARDAR INFORMACION DE UNA VARIABLE
        static class VarInfo {
            String tipo;
            int linea;
            //RECIBE EL TIPO DE LA VARIABLE Y LA LINEA DONDE APARECE
            VarInfo(String tipo, int linea) {
                this.tipo = tipo;
                this.linea = linea;
            }
        }
        Map<String, VarInfo> declaradas = new HashMap<>(); //CONJUNTO QUE GUARDA LOS NOMBRES DE LAS VARIBLES DECLARADAS
        Set<String> usadas = new HashSet<>(); //CONJUNTO QUE GUARDA LOS NOMBRES DE LAS VARIBLES USADAS

        //RECIBE EL PROGRAMA COMPLETO YA CONVERTIDO A AST
        void analyze(List<Stmt> program) {
            if (program == null) {
                return; // evita errores internos si el parser falló
            }

            for (Stmt s : program) {
                checkStmt(s);
            }

            // VERIFICA SI TODAS LAS VARIBLES DECLARADAS FUERON USADAS
            for (Map.Entry<String, VarInfo> entry : declaradas.entrySet()) {
                if (!usadas.contains(entry.getKey())) {
                    reporter.add(new CompileError(
                            "Error Semántico",
                            16,
                            "Variable sin usar",
                            "La variable '" + entry.getKey() + "' fue declarada pero nunca fue usada",
                            entry.getValue().linea
                    ));
                }
            }
        }

        void checkStmt(Stmt s) {   //ANALIZA UNA SENTENCIA

            if (s == null) {
                return; // evita errores internos
            }
            // Verifica si la sentencia es una declaración de variable
            if (s instanceof StmtDeclare sd) {
                if (declaradas.containsKey(sd.name)) {
                    reporter.add(new CompileError("Error Semántico", 17,
                            "Variable redeclarada",
                            "La variable '" + sd.name + "' ya fue declarada",
                            sd.line));
                    return;
                }
                checkExpr(sd.expr);
                checkTipoExpr(sd.tipo, sd.expr, sd.line);
                declaradas.put(sd.name, new VarInfo(sd.tipo, sd.line));
                return;
            }
            
            // Verifica si la sentencia es una asignación de valor a una variable
            if (s instanceof StmtAssign sa) {
                if (!declaradas.containsKey(sa.name)) {
                    // Error: intentar asignar variable no declarada
                    reporter.add(new CompileError(
                            "Error Semántico", 9,
                            "Variable no declarada",
                            "La variable '" + sa.name + "' no fue declarada con tipo (usa 🔢 🌊 🔤 ☑️)",
                            sa.line
                    ));
                    return;
                }
                // Ya existe, marcar como usada
                usadas.add(sa.name);
                checkExpr(sa.expr);
                // Verificar que el nuevo valor sea compatible con el tipo original
                checkTipoExpr(declaradas.get(sa.name).tipo, sa.expr, sa.line);
                return;
            }
            
            //VERIFICA UNA INSTUCCION DE LECTURA
            if (s instanceof StmtRead sr) {

                //REVISA SI LA VARIBLE YA EXISTE, Y SI ES ASÍ ES UN ERROR SEMANTICO
                if (declaradas.containsKey(sr.name)) {
                    reporter.add(new CompileError(
                            "ErrorSemántico", 17,
                            "Variable redeclarada",
                            "La variable '" + sr.name + "' ya existe",
                            sr.line
                    ));
                    return;
                }

                declaradas.put(sr.name, new VarInfo("INT", sr.line));//SI NO EXISTE REGISTRAR CORRECTAMENTE
                return;
            }

            // PRINT
            //PARA IMRPIMIR
            if (s instanceof StmtPrint sp) { //SE VALIDA QUE LA INSTRUCCION SEA VALIDA
                if (sp.value == null) {
                    reporter.add(new CompileError(
                            "Error Semántico", 14,
                            "Impresión sin argumento",
                            "La instrucción 📢 requiere un valor o variable a imprimir",
                            sp.line
                    ));
                    return;
                }
                checkExpr(sp.value);
                return;
            }

            // IF
            //ANALIZA UNA ESTRUCUTA CONDICIONAL
            if (s instanceof StmtIf si) {
                if (si.condition == null) {
                    reporter.add(new CompileError(
                            "Error Semántico", 10,
                            "Condición vacia en IF",
                            "La condición del IF (🥺) debe ser una expresión válida",
                            si.line
                    ));
                    return;
                } else {
                    checkExpr(si.condition);//LA CONDICION DEL IF DEBE SER SEMANTICAMENTE VALIDA
                }

                for (Stmt st : si.thenBranch) {//ANALIZA TODAS LAS SENTENCIAS DEL BLOQUE THEN
                    if (st != null) {
                        checkStmt(st);
                    }
                }

                for (Stmt st : si.elseBranch) {//ANALIZA TODAS LAS SENTENCIAS DEL BLOQUE ELSE
                    if (st != null) {
                        checkStmt(st);
                    }
                }

                return;//TERMINA EL ANALISIS DEL IF
            }

            // WHILE
            if (s instanceof StmtWhile sw) {
                if (sw.condition == null) {
                    reporter.add(new CompileError(
                            "Error Semántico", 11,
                            "Condición vacia en WHILE",
                            "La condición del WHILE (🥺) debe ser una expresión válida",
                            sw.line
                    ));
                    return;
                } else {
                    checkExpr(sw.condition); //LA CONDICION DEL CICLO WHILE DEBE SER VALIDA
                }

                //SE ANALIZA TODAS LAS SENTENCIAS DENTRO DEL CICLO
                for (Stmt st : sw.body) {
                    if (st != null) {
                        checkStmt(st);
                    }
                }

                return; //FIN DEL WHILE
            }

            //ERROR DE RESPALDO
            //ENCONTRAR UN ERROR DESCONOCIDO DE CLASE SEMANTICA
            reporter.add(new CompileError(
                    "Semántico",
                    18,
                    "Instrucción desconocida",
                    "Sentencia no reconocida",
                    -1
            ));
        }
        
        String tipoExpr(Expr e) {

        if (e instanceof ExprNumber) {
            return "INT";
        }

        if (e instanceof ExprString) {
            return "STRING";
        }

        if (e instanceof ExprBool) {
            return "BOOL";
        }

        if (e instanceof ExprVar v) {

            if (!declaradas.containsKey(v.name)) {
                return "ERROR";
            }

            return declaradas.get(v.name).tipo;
        }

        if (e instanceof ExprBinary b) {

            String izq = tipoExpr(b.left);
            String der = tipoExpr(b.right);

            if (izq.equals("ERROR") || der.equals("ERROR")) {
                return "ERROR";
            }

            // operaciones matemáticas
            if (b.op.equals("➕") || b.op.equals("➖") ||
                b.op.equals("✖️") || b.op.equals("✖") ||
                b.op.equals("➗")) {

                if ((izq.equals("INT") || izq.equals("FLOAT")) &&
                    (der.equals("INT") || der.equals("FLOAT"))) {

                    if (izq.equals("FLOAT") || der.equals("FLOAT"))
                        return "FLOAT";

                    return "INT";
                }

                return "ERROR";
            }

            // comparaciones
            if (b.op.equals("⚖️") || b.op.equals("🚫") ||
                b.op.equals("🔼") || b.op.equals("🔽") ||
                b.op.equals("⏫") || b.op.equals("⏬")) {

                return "BOOL";
            }

            // lógicas
            if (b.op.equals("🤝") || b.op.equals("🔀")) {
                return "BOOL";
            }
        }

        return "ERROR";
    }


        // Verifica que el tipo de la expresión coincida con el tipo esperado de la variable
        void checkTipoExpr(String tipoEsperado, Expr expr, int line) {

            if (expr == null) return;

            String tipoReal = tipoExpr(expr);

            if (tipoReal.equals("ERROR")) {

                reporter.add(new CompileError(
                        "Error Semántico",
                        23,
                        "Operación inválida",
                        "Tipos incompatibles en la expresión",
                        line
                ));

                return;
            }

            if (!tipoEsperado.equals(tipoReal)) {

                reporter.add(new CompileError(
                        "Error Semántico",
                        22,
                        "Tipo incompatible",
                        "Se esperaba '" + tipoEsperado +
                        "' pero la expresión produce '" + tipoReal + "'",
                        line
                ));
            }
        }

        void checkExpr(Expr e) {    //ANALIZA UNA EXPRESION

            if (e == null) {
                return; // RECUPERACIUON SEGURA EVITA FALLOS SI HUBO ERRORES ANTES
            }
            if (e instanceof ExprNumber) { //UN NUERO SIEMPRE ES VALIDOS SEMÁNTICAMENTE
                return;
            }
            
            //UN STRING LITERAL SIEMPRE ES VALIDO SEMANTICAMENTE
            if (e instanceof ExprString) {
                return;
            }

            //UN BOOLEANO LITERAL SIEMPRE ES VALIDO SEMANTICAMENTE
            if (e instanceof ExprBool) {
                return;
            }
            
            // Verifica si la expresión es una variable
            if (e instanceof ExprVar v) {
                if (!declaradas.containsKey(v.name)) {
                    reporter.add(new CompileError(
                            "Error Semántico", 9,
                            "Variable no declarada",
                            "La variable '" + v.name + "' se usa antes de ser definida",
                            v.line
                    ));
                } else {
                    usadas.add(v.name); //REGISTRA QUE LA VARIABLE FUE USADA
                }
                return;
            }
            // Verifica si la expresión es una operación binaria (dos operandos)

            if (e instanceof ExprBinary b) {
                if (b.left == null || b.right == null) {
                    reporter.add(new CompileError(
                            "Error Semántico",
                            11,
                            "Operación incompleta",
                            "La operación '" + b.op + "' requiere dos operandos",
                            b.line
                    ));
                    return;
                }
            checkExpr(b.left);
            checkExpr(b.right);

            String t = tipoExpr(b);

            if (t.equals("ERROR")) {
                reporter.add(new CompileError(
                        "Error Semántico",
                        23,
                        "Operación inválida",
                        "Tipos incompatibles en la operación '" + b.op + "'",
                        b.line
                ));
            }
                return;
            }

            //VERIFICA SI LA EXPRESION NO ES NUMERO, VARIABLE, U OPERACINON BINARIA
            reporter.add(new CompileError(
                    "Error Semántico",
                    11,
                    "Expresión inválida",
                    "No se pudo analizar la expresión",
                    e.line
            ));
        }
    }

    //INTERPRETER
    //EJECUTA EL PROGARAMA YA ANALIZADO(AST)
    static class Interpreter {

        //TABLA DE SIMBOLOS EN TIEMPO DE EJECUCION
        //GUARDA VATIABLES Y SUS VALORES
        Map<String, Double> variables = new HashMap<>();
        JTextArea output;
        JTable tabla;

        //CONSTRUCTOR
        Interpreter(JTextArea out, JTable t) {
            output = out;
            tabla = t;
        }

        //EJECUTA CADA SENTENCIA UNA POR UNA DEL PROGRAMA
        void exec(List<Stmt> program) {
            for (Stmt s : program) {
                execute(s);
            }
        }

        //ACTUALIZAR LA TABLA DE VARIABLES
        void actualizarTabla() { //METODO AUXILIAR PARA REFRESCAR LA TABLA
            //LIMPIA LA TABLA ANTES DE VOLVER A LLENARLA
            DefaultTableModel model = (DefaultTableModel) tabla.getModel();
            model.setRowCount(0);

            //INSERTA CADA VARIABLE Y SU VALOR ACTUAL EN LA TABLA
            for (Map.Entry<String, Double> e : variables.entrySet()) {
                model.addRow(new Object[]{e.getKey(), e.getValue()});
            }
        }

        //IDENTIFICA QUE TIPO DE INSTRUCCION ES Y LA EJECUTA
        void execute(Stmt s) {

            //VERIFICA SI ES UN IF
            if (s instanceof StmtIf si) {
                if (si.condition == null) {
                    return; // PROTECCION POR SI HUBO ERRORES ANTES
                }
                double cond = eval(si.condition);//EVALUA LA CONDICION

                if (cond != 0) {
                    for (Stmt st : si.thenBranch) {
                        if (st != null) {
                            execute(st);
                        }
                    }//EJECUTA EL BLOQUE THEN
                } else {
                    for (Stmt st : si.elseBranch) {
                        if (st != null) {
                            execute(st);
                        }
                    }//EJECUTA EL BLOQUE ELSE
                }

                //WHILE CON PROTECCION DEL BUCLE INFINITO
            } else if (s instanceof StmtWhile sw) {
                if (sw.condition == null) {
                    return; // PROTECCION POR SI HUBO ERRORES ANTES
                }
                //CONTADOR DE SEGURIDAD PARA EVITAR CICLOS INFINITOS
                int iteraciones = 0;
                final int MAX_ITERACIONES = 50;

                while (eval(sw.condition) != 0) {//MIENTRAS LA CONDICION SEA VERDADERA
                    iteraciones++;
                    if (iteraciones > MAX_ITERACIONES) {//DETECTA BUCLE INFINITO
                        //REGISTRA EL ERROR Y DENTIENE EL WHILE
                        reporter.add(new CompileError(
                                "Ejecución",
                                19,
                                "Bucle infinito detectado",
                                "El ciclo WHILE excedió " + MAX_ITERACIONES + " iteraciones sin terminar",
                                sw.line
                        ));
                        return; //DETENER EJECUCION DEL WHULE
                    }

                    //EJECUTA EL CUERPO DEL CICLO
                    for (Stmt st : sw.body) {
                        if (st != null) {
                            execute(st);
                        }

                    }
                }

                //EVALUA LA EXPRESION
            } else if (s instanceof StmtPrint sp) {
                if (sp.value == null) {
                    reporter.add(new CompileError(
                            "Ejecución",
                            15,
                            "Impresión sin argumento",
                            "La instrucción 📢 requiere un valor o variable a imprimir",
                            sp.line
                    ));
                    return;
                }
                //IMPRIME EL RESULTADO EN PANTALLA
                double v = eval(sp.value);
                output.append(v + "\n");

                //PIDE UN VALOR AL USUARIO
            } else if (s instanceof StmtRead sr) {

                String val = JOptionPane.showInputDialog(
                        "Valor para " + sr.name + ":"
                );

                if (val == null) { //USUARIO CANCELO LA ENTRADA
                    //SE REGISTRA EL ERROR
                    reporter.add(new CompileError(
                            "Ejecución",
                            13,
                            "Lectura cancelada",
                            "El usuario canceló la entrada",
                            -1
                    ));
                    return;
                }

                try {
                    double num = Double.parseDouble(val);   //CONVIERTE EL VALOR EN NUMERICO
                    variables.put(sr.name, num);    //LO GUARDA EN LA VARIABLE
                    actualizarTabla(); //ACTUALIZA LA TABLA
                } catch (NumberFormatException ex) { //ERROR SI EL USUARIO INGRESA ALGO QUE NO ES NUMERO
                    throw new RuntimeException(
                            "❌ Error 10: Valor no numérico para la variable " + sr.name
                    );
                }

                //ASIGNACION
            } else if (s instanceof StmtAssign sa) {
                if (sa.expr == null) {// PROTECCION POR SI HUBO ERRORES ANTES
                    reporter.add(new CompileError(
                            "Ejecución", 7,
                            "Asignación mal formada",
                            "La variable '" + sa.name + "' no tiene valor",
                            sa.line
                    ));
                    return;
                }
                //EVALUA LA EXPRESION
                double v = eval(sa.expr);
                variables.put(sa.name, v);  //ASIGNA EL VALOR DE LA VARIABLE
                actualizarTabla();

                //PROTECCION POR SI APARECE ALGO DESCONODIDO
            } else {
                reporter.add(new CompileError(
                        "Ejecución",
                        18,
                        "Instrucción desconocida",
                        "No se pudo ejecutar la instrucción",
                        -1
                ));
                return;
            }
        }

        double eval(Expr e) {

            if (e == null) {
                reporter.add(new CompileError(
                        "Ejecución", 10,
                        "Expresión nula",
                        "Se intentó evaluar una expresión vacía",
                        -1
                ));
                return 0; // valor neutro para continuar
            }

            // NÚMERO
            if (e instanceof ExprNumber n) {
                return n.value;
            }

            // VARIABLE
            if (e instanceof ExprVar v) {

                if (!variables.containsKey(v.name)) {
                    reporter.add(new CompileError(
                            "Semántico",
                            9,
                            "Variable no declarada",
                            "La variable '" + v.name + "' no ha sido definida",
                            -1
                    ));
                    return 0; // valor neutro para continuar
                }

                return variables.get(v.name);
            }

            // OPERACIÓN BINARIA
            if (e instanceof ExprBinary b) {

                double l = eval(b.left);
                double r = eval(b.right);

                switch (b.op) {
                    case "⚖️":
                        return (l == r) ? 1 : 0;

                    case "🚫":
                        return (l != r) ? 1 : 0;

                    case "🔼":
                        return (l > r) ? 1 : 0;

                    case "🔽":
                        return (l < r) ? 1 : 0;

                    case "⏫":
                        return (l >= r) ? 1 : 0;

                    case "⏬":
                        return (l <= r) ? 1 : 0;

                    case "🤝":
                        return (l != 0 && r != 0) ? 1 : 0;

                    case "🔀":
                        return (l != 0 || r != 0) ? 1 : 0;
                    case "➕":
                        return l + r;

                    case "➖":
                        return l - r;

                    case "✖️":
                    case "✖":
                        return l * r;

                    case "➗":
                        if (r == 0) {
                            reporter.add(new CompileError(
                                    "Ejecución",
                                    12,
                                    "División entre cero",
                                    "El divisor evaluó a 0",
                                    b.line
                            ));
                            return 0; // continuar ejecución
                        }
                        return l / r;

                    default:
                        reporter.add(new CompileError(
                                "Ejecución",
                                18,
                                "Operador desconocido",
                                "Operador '" + b.op + "' no válido",
                                -1
                        ));
                        return 0;
                }
            }

            // EXPRESIÓN DESCONOCIDA
            reporter.add(new CompileError(
                    "Semántico",
                    11,
                    "Expresión inválida",
                    "No se pudo evaluar la expresión",
                    -1
            ));
            return 0;
        }
    }

    //IMPRESOR DEL ARBOL DE DERIVACION
    static class ArbolPrinter {

        //PUNTO DE ENTRADA
        static String imprimirPrograma(List<Stmt> program) {
            StringBuilder sb = new StringBuilder(); //OBJETO PARA CONTRUIR TEXTO GRANDE
            sb.append("PROGRAMA\n");    //RAIZ DEL ARBOL
            for (Stmt s : program) {
                imprimirStmt(s, "  ", sb);  //LLAMA A IMPRIMIR IMPRIMIRSTMT CON SANGRIA INICIAL
            }
            return sb.toString(); //DEVUELVE EL ARBOL COMO TEXTO
        }

        static void imprimirStmt(Stmt s, String pref, StringBuilder sb) {   //IMPRIME UNA INSTRUCCION DEL PROGRAMA

            //ASIGNACION
            if (s instanceof StmtAssign sa) {
                sb.append(pref).append("Asignación\n"); //NODO PRINCIPAL
                sb.append(pref).append("├─ Variable: ").append(sa.name).append("\n");
                sb.append(pref).append("└─ Expresión\n");
                imprimirExpr(sa.expr, pref + "   ", sb);
                //IMPRIME LA EXPRESION QUE SE VA A MOSTRAR
            } else if (s instanceof StmtPrint sp) {
                sb.append(pref).append("Imprimir\n");
                imprimirExpr(sp.value, pref + "  ", sb);
                //NODO READ MUESTRA LA VARIABLE DONDE GUARDARA EL VALOR
            } else if (s instanceof StmtRead sr) {
                sb.append(pref).append("Leer\n");
                sb.append(pref).append("└─ Variable: ").append(sr.name).append("\n");
                //NODO PRINCIPAL
            } else if (s instanceof StmtIf si) {
                sb.append(pref).append("IF\n");//HIJO CONDICION DEL IF
                //BLOQUE VERDADERO
                sb.append(pref).append("├─ Condición\n");
                imprimirExpr(si.condition, pref + "│  ", sb);

                sb.append(pref).append("├─ THEN\n");
                //RECORRE INSTRUCCIONES DEL THEN
                for (Stmt st : si.thenBranch) {
                    imprimirStmt(st, pref + "│  ", sb);
                }

                //SOLO IMPRIME ELSE SI EXISTE
                if (!si.elseBranch.isEmpty()) {
                    sb.append(pref).append("└─ ELSE\n");
                    //RAMA ALTSA
                    for (Stmt st : si.elseBranch) {
                        imprimirStmt(st, pref + "   ", sb);
                    }
                }
                //NODO WHILE
            } else if (s instanceof StmtWhile sw) {
                sb.append(pref).append("WHILE\n");
                //CONDICION DEL CICLO
                sb.append(pref).append("├─ Condición\n");

                imprimirExpr(sw.condition, pref + "│  ", sb);
                //CUERPO DEL CICLO
                sb.append(pref).append("└─ Cuerpo\n");
                for (Stmt st : sw.body) {
                    imprimirStmt(st, pref + "   ", sb);
                }
            }
        }

        //IMPRIME NODOS DE EXPRESION DEL AST
        static void imprimirExpr(Expr e, String pref, StringBuilder sb) {
            //HOJA DEL ARBOL VALOR CONSTANTE
            if (e instanceof ExprNumber n) {
                sb.append(pref).append("Número: ").append(n.value).append("\n");
                //VARIABLE, HOJA DEL ARBOL RE FERENCIA A UNA VARIABLE
            } else if (e instanceof ExprVar v) {
                sb.append(pref).append("Variable: ").append(v.name).append("\n");
                //OPERACION BINARIA NODO OPERADOR + -
            } else if (e instanceof ExprBinary b) {
                sb.append(pref).append("Operación ").append(b.op).append("\n");
                //OPERANDO IZQUIERDO
                sb.append(pref).append("├─ Izquierda\n");
                imprimirExpr(b.left, pref + "│  ", sb);
                //OPERANDO DERECHO
                sb.append(pref).append("└─ Derecha\n");
                imprimirExpr(b.right, pref + "   ", sb);
            }
        }
    }

    //MAIN(IDE GRAFICO)
    public static void main(String[] args) {

        //EVITA ERRORES DE CONCURRENCIA
        SwingUtilities.invokeLater(() -> {

            //GUARDAR EL ULTIMO PROGRAMA VALIDO
            final List<Stmt>[] ultimoPrograma = new List[]{null};

            //VENTANA PRINCIPAL
            JFrame f = new JFrame("EmojiLang IDE Avanzado");
            f.setSize(1000, 750);
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            f.setLocationRelativeTo(null);

            // EDITOR
            JTextArea editor = new JTextArea();
            editor.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 19));

            //AGREGAR SCROLL AL EDITOR
            JScrollPane editorScroll = new JScrollPane(editor);

            //AGREGA NUMEROS DE LINEA
            LineNumberArea lineNumbers = new LineNumberArea(editor);
            editorScroll.getViewport().addChangeListener(e -> lineNumbers.repaint());
            editorScroll.setRowHeaderView(lineNumbers);

            // CONSOLAS
            //SALIDA DE PRINT
            JTextArea salida = new JTextArea();
            salida.setEditable(false);

            //MOSTRAR LOS TOKENS DEL LEXER
            JTextArea tokens = new JTextArea();
            tokens.setEditable(false);

            //MOSTRAR ERRORES
            JTextArea errores = new JTextArea();
            errores.setEditable(false);

            //MUESTRA VARIABLES Y VALORES EN TIEMPO DE EJECUCION
            JTable tabla = new JTable(
                    new DefaultTableModel(new Object[]{"Variable", "Valor"}, 0)
            );

            //CREA PESTAÑAS PARA ORGANIZAR SALUDOS
            JTabbedPane tabs = new JTabbedPane();
            tabs.add("Salida", new JScrollPane(salida));
            tabs.add("Tokens", new JScrollPane(tokens));
            tabs.add("Errores", new JScrollPane(errores));
            tabs.add("Tabla", new JScrollPane(tabla));

            // BOTONES
            JButton open = new JButton("📂 Abrir");
            JButton save = new JButton("💾 Guardar");
            JButton run = new JButton("▶ Ejecutar");
            JButton help = new JButton("❌ Errores");
            JButton arbol = new JButton("🌳 Árbol");
            arbol.setEnabled(false);

            // 📂 ABRIR ARCHIVO
            open.addActionListener(e -> {
                JFileChooser chooser = new JFileChooser();
                int result = chooser.showOpenDialog(f);

                if (result == JFileChooser.APPROVE_OPTION) {
                    try {
                        java.nio.file.Path path = chooser.getSelectedFile().toPath();
                        String contenido = java.nio.file.Files.readString(path);
                        editor.setText(contenido);
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(
                                f,
                                "❌ Error al abrir el archivo",
                                "Error",
                                JOptionPane.ERROR_MESSAGE
                        );
                    }
                }
            });

            // 💾 GUARDAR ARCHIVO
            save.addActionListener(e -> {
                JFileChooser chooser = new JFileChooser();
                int result = chooser.showSaveDialog(f);

                if (result == JFileChooser.APPROVE_OPTION) {
                    try {
                        java.nio.file.Path path = chooser.getSelectedFile().toPath();
                        java.nio.file.Files.writeString(path, editor.getText());
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(
                                f,
                                "❌ Error al guardar el archivo",
                                "Error",
                                JOptionPane.ERROR_MESSAGE
                        );
                    }
                }
            });

            help.addActionListener(e
                    -> new ErrorHelpWindow().setVisible(true)
            );

            //BOTON DE ARBOL DE DERIVACION
            arbol.addActionListener(e -> {
                //SINO HAY PROGRAMA VALIDO, NO SE PUEDE MOSTRAR EL ARBOL
                if (ultimoPrograma[0] == null) {
                    JOptionPane.showMessageDialog(
                            null,
                            "❌ No hay árbol válido.\nEjecuta el programa sin errores.",
                            "Árbol no disponible",
                            JOptionPane.WARNING_MESSAGE
                    );
                    return;
                }

                //AREA DONDE SE MOSTRARA EL ARBOL
                JTextArea area = new JTextArea();
                area.setEditable(false);
                area.setFont(new Font("Consolas", Font.PLAIN, 14));

                //GENERA EL ARBOL DESDE EL AST
                area.setText(
                        ArbolPrinter.imprimirPrograma(ultimoPrograma[0])
                );

                JScrollPane sp = new JScrollPane(area);
                sp.setPreferredSize(new Dimension(500, 400));

                //MUESTRA EL ARBOL EN UN DIALOGO
                JOptionPane.showMessageDialog(
                        null,
                        sp,
                        "Árbol de Derivación",
                        JOptionPane.INFORMATION_MESSAGE
                );
            });

            run.addActionListener(e -> {
                //LIMPIAR TODO
                salida.setText("");
                tokens.setText("");
                errores.setText("");
                reporter.errores.clear();
                // ⛔ invalidar árbol antes de ejecutar
                ultimoPrograma[0] = null;
                arbol.setEnabled(false);

                List<Stmt> programa = null;

                try {
                    Lexer lex = new Lexer(editor.getText());
                    lex.scan();

                    if (reporter.hasErrors()) {
                        reporter.print(errores);
                        return; // ⛔ NO continuar
                    }

                    Parser parser = new Parser(lex);
                    programa = parser.parseProgram();

                    SemanticAnalyzer sem = new SemanticAnalyzer();
                    sem.analyze(programa);

                    if (reporter.hasErrors()) {
                        reporter.print(errores);
                        return;
                    }

                    /*
                    Interpreter inter = new Interpreter(salida, tabla);
                    inter.exec(programa);
                    

                    if (reporter.hasErrors()) {
                        reporter.print(errores); // <--- FALTA ESTO DESPUÉS DE EXEC
                        return;
                    }

                     */
                    salida.append("COMPILACIÓN EXITOSA\n");

                    // ✅ ejecución correcta → guardar árbol
                    ultimoPrograma[0] = programa;
                    arbol.setEnabled(true);

                    for (Token t : lex.tokens) {
                        tokens.append(t.toString() + "\n");
                    }
                    tokens.append("FIN DEL CÓDIGO\n");

                } catch (Exception ex) {
                    errores.append("❌ Error inesperado\n");
                    errores.append(ex.toString());
                }

                if (reporter.hasErrors()) {
                    arbol.setEnabled(false);
                    return;
                }

                // ✅ ya existe aquí
                ultimoPrograma[0] = programa;
                arbol.setEnabled(true);
            });

            // PANEL SUPERIOR
            JPanel top = new JPanel();
            top.add(open);
            top.add(save);
            top.add(run);
            top.add(help);
            top.add(arbol);

            // PANEL AYUDA EMOJIS
            EmojiHelpPanel emojiPanel = new EmojiHelpPanel();

            // SPLITS
            JSplitPane horizontalSplit = new JSplitPane(
                    JSplitPane.HORIZONTAL_SPLIT,
                    editorScroll,
                    emojiPanel
            );
            horizontalSplit.setDividerLocation(650);

            JSplitPane split = new JSplitPane(
                    JSplitPane.VERTICAL_SPLIT,
                    horizontalSplit,
                    tabs
            );
            split.setDividerLocation(420);

            // AGREGAR AL FRAME (ESTO ERA LO QUE FALTABA)
            f.add(top, BorderLayout.NORTH);
            f.add(split, BorderLayout.CENTER);

            f.setVisible(true);
        });
    }
}
