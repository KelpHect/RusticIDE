from typing import Optional
import pandas as pd

series = pd.Series([1, 2, 3])
other_series = pd.Series([4, 5, 6])

if <warning descr="The truth value of a Series is ambiguous">series</warning>: ...

if <warning descr="The truth value of a Series is ambiguous">series</warning> == None: ...

if <warning descr="The truth value of a Series is ambiguous">not series</warning>: ...

if <warning descr="The truth value of a Series is ambiguous">series</warning> == <warning descr="The truth value of a Series is ambiguous">other_series</warning>: ...

series_1: Optional[pd.Series] = series
if <warning descr="The truth value of a Series is ambiguous">series_1</warning>: ...

def e(): ...
if <warning descr="The truth value of a Series is ambiguous">series</warning> or e(): ...

if <warning descr="The truth value of a Series is ambiguous">series</warning> and <warning descr="The truth value of a Series is ambiguous">other_series</warning>: ...

if <warning descr="The truth value of a Series is ambiguous">series</warning> > 5: ...

if <warning descr="The truth value of a Series is ambiguous">series</warning> and (<warning descr="The truth value of a Series is ambiguous">series</warning> or <warning descr="The truth value of a Series is ambiguous">series</warning>): ...

while <warning descr="The truth value of a Series is ambiguous">series</warning>: ...

assert <warning descr="The truth value of a Series is ambiguous">series</warning>

assert <warning descr="The truth value of a Series is ambiguous">not series</warning>

if series is None: ...

if series.empty: ...

if not series.empty: ...
