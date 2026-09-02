" Automated regression test for the concise-method-body Vim syntax add-on.
"
" Asserts, after the add-on is AUTO-LOADED via ~/.vim/after/syntax/java.vim, that:
"   - the three syntax items exist (javaConciseMarker, javaConciseArrowBody,
"     javaConciseRefBody),
"   - the marker links to Operator,
"   - the region start patterns are anchored on a preceding ')' (so ordinary
"     assignments are never matched).
"
" This level is deterministic headless (unlike synID, which needs screen
" rendering). It guards against the most likely regressions: a broken start
" pattern, a renamed group, or the re-entrancy bug where our items were cleared
" by a second load of the standard java.vim.

func! RunConciseSyntaxTests(outfile) abort
  let l:r = []
  let l:ok = 1

  redir => l:dump
  silent! syntax list
  redir END

  " 1) All three items present.
  for l:name in ['javaConciseMarker', 'javaConciseArrowBody', 'javaConciseRefBody']
    if l:dump =~# l:name
      call add(l:r, 'PASS defined: ' . l:name)
    else
      call add(l:r, 'FAIL missing: ' . l:name)
      let l:ok = 0
    endif
  endfor

  " 2) Marker links to Operator.
  redir => l:marker
  silent! syntax list javaConciseMarker
  redir END
  if l:marker =~# 'links to Operator'
    call add(l:r, 'PASS marker links to Operator')
  else
    call add(l:r, 'FAIL marker not linked to Operator')
    let l:ok = 0
  endif

  " 3) Arrow region is anchored on a preceding ')' and matches '->'.
  redir => l:arrow
  silent! syntax list javaConciseArrowBody
  redir END
  if l:arrow =~# ')\\@<=' && l:arrow =~# '->'
    call add(l:r, 'PASS arrow region anchored on ) and matches ->')
  else
    call add(l:r, 'FAIL arrow region pattern wrong')
    let l:ok = 0
  endif

  " 4) Ref region is anchored on a preceding ')' and matches '='.
  redir => l:ref
  silent! syntax list javaConciseRefBody
  redir END
  if l:ref =~# ')\\@<=' && l:ref =~# 'zs='
    call add(l:r, 'PASS ref region anchored on ) and matches =')
  else
    call add(l:r, 'FAIL ref region pattern wrong')
    let l:ok = 0
  endif

  call add(l:r, l:ok ? 'ALL_PASS' : 'SOME_FAIL')
  call writefile(l:r, a:outfile)
endfunc
