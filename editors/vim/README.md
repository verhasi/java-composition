# Editor Support — Vim / Neovim

Syntax highlighting for concise method bodies (JEP 8209434) in Vim and Neovim.
This is a lightweight **syntax add-on**: it extends the standard Java syntax to color
the concise markers `->` and `=` and their payload. It does not replace the built-in
Java syntax, and standard `{ ... }` bodies are untouched.

```java
public int size()            -> c.size();      // -> colored as an operator, c.size() as Java
public boolean isEmpty()     -> c.isEmpty();
static int max(int a, int b) = Math::max;      // = colored as an operator, Math::max as Java
```

## What it highlights

- The concise **markers** `->` and `=` in method-declaration position, colored as
  operators (inherits your theme's operator color).
- The **payload** expression after the marker, using standard Java token groups so it
  matches the rest of your code.

The `=` marker is only highlighted when it follows a method's `)` (method-body position),
so ordinary assignments (`int x = 5;`) are never affected.

## Install (Vim)

Copy the syntax file into your Vim runtime's `after/syntax` directory so it extends the
standard Java syntax:

```sh
mkdir -p ~/.vim/after/syntax
cp editors/vim/after/syntax/java.vim ~/.vim/after/syntax/java.vim
```

Open any `.java` file with concise bodies — the markers and payload are highlighted.

## Install (Neovim)

Neovim reads the same location under its config directory:

```sh
mkdir -p ~/.config/nvim/after/syntax
cp editors/vim/after/syntax/java.vim ~/.config/nvim/after/syntax/java.vim
```

(For Tree-sitter-based highlighting in Neovim, a Tree-sitter query variant is planned —
see `docs/plan-ide-support.md`, Phase A.)

## Verify

Open the bundled example and confirm the markers/payload are colored:

```sh
vim editors/vim/sample-concise.java
```

Place the cursor on a `->` or `=` marker and run `:echo synIDattr(synID(line('.'), col('.'), 1), 'name')`
— it reports `javaConciseMarker`. On the payload it reports `javaConciseArrowBody` or
`javaConciseRefBody`. On an ordinary assignment `=` it reports the standard Java group
(never `javaConciseMarker`).
