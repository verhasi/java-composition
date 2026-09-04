# Editor Support — Vim / Neovim

Syntax highlighting for concise method bodies (JEP 8209434) in Vim and Neovim.
This is a lightweight **syntax add-on**: it extends the standard Java syntax to color
the concise markers `->` and `=` and their payload. It does not replace the built-in
Java syntax, and standard `{ ... }` bodies are untouched.

![Concise method bodies highlighted in Vim: the -> and = markers are colored as operators and their payload as ordinary Java, while a standard { ... } body is unchanged.](screenshot.png)

*Screenshot of `sample-concise.java` in Vim with this add-on active. Colors reflect the
active colorscheme — yours will match your own theme.*

The same source as plain text (GitHub cannot highlight the concise syntax — that is exactly
what this add-on adds in your editor):

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

## Install (plugin manager — recommended)

The plugin is published to its own repository, **[verhasi/java-composition.vim]**, whose
root *is* the plugin — so it installs with a plain one-liner and a tiny clone (no need to
pull the whole `java-composition` project). Development happens in this monorepo under
`editors/vim/`; that content is deployed to the plugin repo (see `deploy.sh`).

[verhasi/java-composition.vim]: https://github.com/verhasi/java-composition.vim

**vim-plug** (Vim or Neovim):

```vim
Plug 'verhasi/java-composition.vim'
```

**lazy.nvim** (Neovim):

```lua
{ "verhasi/java-composition.vim", lazy = false }
```

**packer.nvim** (Neovim):

```lua
use "verhasi/java-composition.vim"
```

**Native packages** (no manager, Vim 8+/Neovim):

```sh
git clone https://github.com/verhasi/java-composition.vim \
  ~/.vim/pack/plugins/start/java-composition
```

After installing, open any `.java` file with concise bodies — the markers and payload are
highlighted. No `rtp`/subdirectory option is needed: the add-on sits at the plugin repo's
root (`after/syntax/java.vim`).

## Install (manual copy)

If you prefer not to use a plugin manager, copy the single syntax file into your Vim
runtime's `after/syntax` directory so it extends the standard Java syntax:

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

## Method references

The `::` in a method reference (`Math::max`) is colored using the standard `javaMethodRef`
group, so it looks the same inside a concise body as it does anywhere else in your Java
code. Bare identifiers around it (`Math`, `max`) follow Vim's normal Java coloring — Vim's
standard syntax does not specially color method-reference identifiers, and this add-on stays
consistent with that.

## Automated test

A regression test asserts that the add-on's syntax items load correctly (via the real
`~/.vim/after/syntax/` auto-load path) and that the markers are anchored on a preceding `)`:

```sh
./editors/vim/test/run-tests.sh
```

It checks group definitions (deterministic headless) rather than rendered colors, guarding
against broken patterns, renamed groups, and the reload bug where a second load of the
standard `java.vim` could clear the add-on's items.

## Verify manually

Open the bundled example and confirm the markers/payload are colored:

```sh
vim editors/vim/sample-concise.java
```

Place the cursor on a `->` or `=` marker and run `:echo synIDattr(synID(line('.'), col('.'), 1), 'name')`
— it reports `javaConciseMarker`. On the payload it reports `javaConciseArrowBody` or
`javaConciseRefBody`. On an ordinary assignment `=` it reports the standard Java group
(never `javaConciseMarker`).
