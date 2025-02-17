# coding=utf-8
# Copyright (c) 2017, 2021, Oracle and/or its affiliates.
# Copyright (c) 2017, The PyPy Project
#
#     The MIT License
# Permission is hereby granted, free of charge, to any person
# obtaining a copy of this software and associated documentation
# files (the "Software"), to deal in the Software without
# restriction, including without limitation the rights to use,
# copy, modify, merge, publish, distribute, sublicense, and/or
# sell copies of the Software, and to permit persons to whom the
# Software is furnished to do so, subject to the following conditions:
#
# The above copyright notice and this permission notice shall be included
# in all copies or substantial portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS
# OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
# THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
# FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
# DEALINGS IN THE SOFTWARE.
import sys


class repeat():
    """
    repeat(object [,times]) -> create an iterator which returns the object\n\
    for the specified number of times.  If not specified, returns the object\n\
    endlessly.
    """
    @__graalpython__.builtin_method
    def __init__(self, object, times=None):
        self.element = object
        if times is not None and not isinstance(times, int):
            raise TypeError(f"integer argument expected, got {times.__class__.__name__}")
        if times is not None:
            if times < 0:
                self.cnt = 0
            else:
                self.cnt = times
        else:
            self.cnt = -1

    @__graalpython__.builtin_method
    def __iter__(self):
        return self

    @__graalpython__.builtin_method
    def __next__(self):
        if self.cnt == 0:
            raise StopIteration
        elif self.cnt > 0:
            self.cnt -= 1
        return self.element

    @__graalpython__.builtin_method
    def __length_hint__(self):
        if self.cnt == -1:
            raise TypeError("len() of unsized object")
        return self.cnt

    @__graalpython__.builtin_method
    def __reduce__(self):
        if self.cnt >= 0:
            return type(self), (self.element, self.cnt)
        return type(self), (self.element,)

    @__graalpython__.builtin_method
    def __repr__(self):
        if self.cnt == -1:
            return "{}({!r})".format(type(self).__name__, self.element)
        else:
            return "{}({!r}, {})".format(type(self).__name__, self.element, self.cnt)


class chain():
    """
    Return a chain object whose .__next__() method returns elements from the
    first iterable until it is exhausted, then elements from the next
    iterable, until all of the iterables are exhausted.
    """
    @__graalpython__.builtin_method
    def __init__(self, *iterables):
        self._source = iter(iterables)
        self._active = None

    @__graalpython__.builtin_method
    def __iter__(self):
        return self

    @__graalpython__.builtin_method
    def __next__(self):
        while self._source:
            if not self._active:
                try:
                    self._active = iter(next(self._source))
                except:
                    self._source = None
                    raise
            try:
                return next(self._active)
            except StopIteration:
                self._active = None
        raise StopIteration

    @classmethod
    @__graalpython__.builtin_method
    def from_iterable(cls, arg):
        instance = cls()
        instance._source = iter(arg)
        return instance

    @__graalpython__.builtin_method
    def __reduce__(self):
        if self._source:
            if self._active:
                return type(self), (), (self._source, self._active)
            else:
                return type(self), (), (self._source,)
        else:
            return type(self), ()

    @__graalpython__.builtin_method
    def __setstate__(self, state):
        if not isinstance(state, tuple) or len(state) not in [1,2]:
            raise TypeError("state is not a length 1 or 2 tuple")
        self._source = state[0]
        if not getattr(self._source, "__next__", None):
            raise TypeError("Arguments must be iterators")
        if len(state) == 2:
            self._active = state[1]
            if not getattr(self._active, "__next__", None):
                raise TypeError("Arguments must be iterators")


class starmap():
    """starmap(function, sequence) --> starmap object

    Return an iterator whose values are returned from the function evaluated
    with an argument tuple taken from the given sequence.
    """
    @__graalpython__.builtin_method
    def __init__(self, fun, iterable):
        self.fun = fun
        self.iterable = iter(iterable)

    @__graalpython__.builtin_method
    def __iter__(self):
        return self

    @__graalpython__.builtin_method
    def __next__(self):
        obj = next(self.iterable)
        return self.fun(*obj)

    @__graalpython__.builtin_method
    def __reduce__(self):
        return type(self), (self.fun, self.iterable)


class islice(object):
    @__graalpython__.builtin_method
    def __init__(self, iterable, *args):
        start = 0
        stop = -1
        step = 1
        if len(args) not in [1, 2, 3]:
            raise TypeError("islice(seq, stop) or islice(seq, start, stop[, step])")
        if len(args) == 1:
            if args[0] != None:
                stop = int(args[0])
        else:
            if args[0] != None:
                try:
                    start = int(args[0])
                except:
                    start = -1
            if args[1] != None:
                try:
                    stop = int(args[1])
                except:
                    raise ValueError("Stop argument for islice() must be None or ean integer: 0 <= x <= sys.maxsize.")
        if start < 0 or stop < -1 or start > sys.maxsize or stop > sys.maxsize:
            raise ValueError("Indices for islice() must be None or an integer: 0 <= x <= sys.maxsize.")
        if len(args) == 3:
            if args[2] != None:
                try:
                    step = int(args[2])
                except:
                    step = -1
        if step < 1:
            raise ValueError("Step for islice() must be a positive integer or None.")

        self._it = iter(iterable)
        self._next = start
        self._stop = stop
        self._step = step
        self._cnt = 0

    @__graalpython__.builtin_method
    def __iter__(self):
        return self

    @__graalpython__.builtin_method
    def __next__(self):
        it = self._it
        stop = self._stop
        if not it:
            raise StopIteration
        while self._cnt < self._next:
            try:
                item = next(it)
            except:
                # C code uses any exception to clear the iterator
                self._it = None
                raise
            self._cnt += 1
        if stop != -1 and self._cnt >= stop:
            self._it = None
            raise StopIteration
        try:
            item = next(it)
        except:
            self._it = None
            raise
        self._cnt += 1
        oldnext = self._next
        self._next += self._step
        if self._next < oldnext or (stop != -1 and self._next > stop):
            self._next = stop
        return item

    @__graalpython__.builtin_method
    def __reduce__(self):
        if self._it is None:
            return type(self), (iter([]), 0), 0
        if self._stop == -1:
            stop = None
        else:
            stop = self._stop
        return type(self), (self._it, self._next, stop, self._step), self._cnt

    @__graalpython__.builtin_method
    def __setstate__(self, state):
        self._cnt = int(state)


class count(object):
    @__graalpython__.builtin_method
    def __init__(self, start=0, step=1):
        valid_start = valid_step = False
        for o in [start, step]:
            if not isinstance(o, complex):
                for mm in ["__index__", "__float__", "__int__"]:
                    if hasattr(o, mm):
                        break
                else:
                    raise TypeError("a number is required")
        self._cnt = start
        self._step = step

    @__graalpython__.builtin_method
    def __next__(self):
        _cnt = self._cnt
        self._cnt += self._step
        return _cnt

    @__graalpython__.builtin_method
    def __repr__(self):
        _repr = '{}({}'.format(type(self).__name__, self._cnt)
        if not isinstance(self._step, int) or self._step != 1:
            _repr += ', {}'.format(self._step)
        return _repr + ')'

    @__graalpython__.builtin_method
    def __iter__(self):
        return self


class permutations():
    """permutations(iterable[, r]) --> permutations object

    Return successive r-length permutations of elements in the iterable.

    permutations(range(3), 2) --> (0,1), (0,2), (1,0), (1,2), (2,0), (2,1)

    """
    @__graalpython__.builtin_method
    def __init__(self, iterable, r = None):
        self.pool = iterable
        if r is None:
            self.r = len(iterable)
        else:
            if not isinstance(r, int):
                raise TypeError("Expected int as r")
            if r < 0:
                raise ValueError("r must be non-negative")
            self.r = r
        n = len(iterable)
        n_minus_r = n - self.r
        if n_minus_r < 0:
            self.stopped = self.raised_stop_iteration = True
        else:
            self.stopped = self.raised_stop_iteration = False
            self.indices = list(range(n))
            self.cycles = list(range(n, n_minus_r, -1))
            self.started = False

    @__graalpython__.builtin_method
    def __iter__(self):
        return self

    @__graalpython__.builtin_method
    def __next__(self):
        if self.stopped:
            self.raised_stop_iteration = True
            raise StopIteration
        r = self.r
        indices = self.indices
        result = tuple([self.pool[indices[i]] for i in range(r)])
        cycles = self.cycles
        i = r - 1
        while i >= 0:
            j = cycles[i] - 1
            if j > 0:
                cycles[i] = j
                indices[i], indices[-j] = indices[-j], indices[i]
                return result
            cycles[i] = len(indices) - i
            n1 = len(indices) - 1
            assert n1 >= 0
            num = indices[i]
            for k in range(i, n1):
                indices[k] = indices[k+1]
            indices[n1] = num
            i -= 1
        self.stopped = True
        if self.started:
            raise StopIteration
        else:
            self.started = True
        return result

    @__graalpython__.builtin_method
    def __reduce__(self):
        if self.raised_stop_iteration:
            pool = []
        else:
            pool = self.pool
        result = [
            type(self),
            tuple([
                tuple(pool), self.r
            ])
        ]
        if not self.raised_stop_iteration:
            # we must pickle the indices and use them for setstate
            result = result + [
                tuple([
                    tuple(self.indices),
                    tuple(self.cycles),
                    self.started,
                ])]
        return tuple(result)

    @__graalpython__.builtin_method
    def __setstate__(self, state):
        state = list(state)
        if len(state) == 3:
            indices, cycles, started = state
            indices = list(indices)
            cycles = list(cycles)
            self.started = bool(started)
        else:
            raise ValueError("invalid arguments")

        if len(indices) != len(self.pool) or len(cycles) != self.r:
            raise ValueError("invalid arguments")

        n = len(self.pool)
        for i in range(n):
            index = indices[i]
            if index < 0:
                index = 0
            elif index > n-1:
                index = n-1
            self.indices[i] = index

        for i in range(self.r):
            index = cycles[i]
            if index < 1:
                index = 1
            elif index > n-i:
                index = n-i
            self.cycles[i] = index


class product():
    """Cartesian product of input iterables.

       Equivalent to nested for-loops in a generator expression. For example,
        ``product(A, B)`` returns the same as ``((x,y) for x in A for y in B)``.

       The nested loops cycle like an odometer with the rightmost element advancing
        on every iteration.  This pattern creates a lexicographic ordering so that if
        the input's iterables are sorted, the product tuples are emitted in sorted
        order.

       To compute the product of an iterable with itself, specify the number of
        repetitions with the optional *repeat* keyword argument.  For example,
        ``product(A, repeat=4)`` means the same as ``product(A, A, A, A)``.

       This function is equivalent to the following code, except that the
        actual implementation does not build up intermediate results in memory::

           def product(*args, **kwds):
               # product('ABCD', 'xy') --> Ax Ay Bx By Cx Cy Dx Dy
               # product(range(2), repeat=3) --> 000 001 010 011 100 101 110 111
               pools = map(tuple, args) * kwds.get('repeat', 1)
               result = [[]]
               for pool in pools:
                   result = [x+[y] for x in result for y in pool]
               for prod in result:
                   yield tuple(prod)
    """
    @__graalpython__.builtin_method
    def __init__(self, *args, repeat=1):
        self.gears = [list(arg) for arg in args] * repeat
        for gear in self.gears:
            if len(gear) == 0:
                self.indices = None
                self.lst = None
                self.stopped = True
                break
        else:
            self.indices = [0] * len(self.gears)
            self.lst = None
            self.stopped = False

    @__graalpython__.builtin_method
    def _rotate_previous_gears(self):
        lst = self.lst
        x = len(self.gears) - 1
        lst[x] = self.gears[x][0]
        self.indices[x] = 0
        x -= 1
        # the outer loop runs as long as a we have a carry
        while x >= 0:
            gear = self.gears[x]
            index = self.indices[x] + 1
            if index < len(gear):
                # no carry: done
                lst[x] = gear[index]
                self.indices[x] = index
                return
            lst[x] = gear[0]
            self.indices[x] = 0
            x -= 1
        else:
            self.lst = None
            self.stopped = True

    @__graalpython__.builtin_method
    def fill_next_result(self):
        # the last gear is done here, in a function with no loop,
        # to allow the JIT to look inside
        if self.lst is None:
            self.lst = [None for gear in self.gears]
            for index, gear in enumerate(self.gears):
                self.lst[index] = gear[0]
            return
        lst = self.lst
        x = len(self.gears) - 1
        if x >= 0:
            gear = self.gears[x]
            index = self.indices[x] + 1
            if index < len(gear):
                # no carry: done
                lst[x] = gear[index]
                self.indices[x] = index
            else:
                self._rotate_previous_gears()
        else:
            self.stopped = True

    @__graalpython__.builtin_method
    def __iter__(self):
        return self

    @__graalpython__.builtin_method
    def __next__(self):
        if not self.stopped:
            self.fill_next_result()
        if self.stopped:
            raise StopIteration
        return tuple(self.lst)

    @__graalpython__.builtin_method
    def __reduce__(self):
        if not self.stopped:
            gears = [tuple(gear) for gear in self.gears]
            result = [
                type(self),
                tuple(gears)
            ]
            if self.lst is not None:
                result = result + [tuple(self.indices)]
        else:
            result = [
                type(self),
                tuple([tuple([])])
            ]
        return tuple(result)

    @__graalpython__.builtin_method
    def __setstate__(self, state):
        gear_count = len(self.gears)
        indices = list(state)
        lst = []
        for i, gear in enumerate(self.gears):
            index = indices[i]
            gear_size = len(gear)
            if self.indices is None or gear_size == 0:
                self.stopped = True
                return
            if index < 0:
                index = 0
            if index > gear_size - 1:
                index = gear_size - 1
            self.indices[i] = index
            lst.append(gear[index])
        self.lst = lst


class accumulate(object):
    """
    "accumulate(iterable) --> accumulate object

    Return series of accumulated sums."""

    _marker = object()

    @__graalpython__.builtin_method
    def __init__(self, iterable, func=None, *, initial=None):
        self.iterable = iter(iterable)
        self.func = func
        self.total = accumulate._marker
        self.initial = initial

    @__graalpython__.builtin_method
    def __iter__(self):
        return self

    @__graalpython__.builtin_method
    def __next__(self):
        if self.initial is not None:
            self.total = self.initial
            self.initial = None
            return self.total
        value = next(self.iterable)
        if self.total is accumulate._marker:
            self.total = value
            return value

        if self.func is None:
            self.total = self.total + value
        else:
            self.total = self.func(self.total, value)
        return self.total

    @__graalpython__.builtin_method
    def __reduce__(self):
        if self.initial is not None:
            it = chain((self.initial,), self.iterable)
            return type(self), (it, self.func), None
        elif self.total is None:
            it = accumulate(chain((self.total,), self.iterable), self.func)
            return islice, (it, 1, None)
        else:
            return type(self), (self.iterable, self.func), self.total

    @__graalpython__.builtin_method
    def __setstate__(self, state):
        self.total = state


class dropwhile(object):
    """
    dropwhile(predicate, iterable) --> dropwhile object

    Drop items from the iterable while predicate(item) is true.
    Afterwards, return every element until the iterable is exhausted.
    """

    @__graalpython__.builtin_method
    def __init__(self, predicate, iterable):
        self.predicate = predicate
        self.iterable = iter(iterable)
        self.done_dropping = False

    @__graalpython__.builtin_method
    def __iter__(self):
        return self

    @__graalpython__.builtin_method
    def __next__(self):
        while not self.done_dropping:
            n = next(self.iterable)
            if self.predicate(n):
                continue
            else:
                self.done_dropping = True
                return n
        return next(self.iterable)


class filterfalse(object):
    """
    filterfalse(function or None, sequence) --> filterfalse object

    Return those items of sequence for which function(item) is false.
    If function is None, return the items that are false.
    """

    @__graalpython__.builtin_method
    def __init__(self, func, sequence):
        self.func = func
        self.iterator = iter(sequence)

    @__graalpython__.builtin_method
    def __iter__(self):
        return self

    @__graalpython__.builtin_method
    def __next__(self):
        while True:
            n = next(self.iterator)
            if self.func is None:
                if not n:
                    return n
            elif not self.func(n):
                return n

    @__graalpython__.builtin_method
    def __reduce__(self):
        return type(self), (self.func, self.iterator)


class takewhile(object):
    """Make an iterator that returns elements from the iterable as
    long as the predicate is true.

    Equivalent to :

    def takewhile(predicate, iterable):
        for x in iterable:
            if predicate(x):
                yield x
            else:
                break
    """
    @__graalpython__.builtin_method
    def __init__(self, predicate, iterable):
        self._predicate = predicate
        self._iter = iter(iterable)

    @__graalpython__.builtin_method
    def __iter__(self):
        return self

    @__graalpython__.builtin_method
    def __next__(self):
        value = next(self._iter)
        if not self._predicate(value):
            self._iter = iter([])
            raise StopIteration()
        return value


class groupby(object):
    """Make an iterator that returns consecutive keys and groups from the
    iterable. The key is a function computing a key value for each
    element. If not specified or is None, key defaults to an identity
    function and returns the element unchanged. Generally, the
    iterable needs to already be sorted on the same key function.

    The returned group is itself an iterator that shares the
    underlying iterable with groupby(). Because the source is shared,
    when the groupby object is advanced, the previous group is no
    longer visible. So, if that data is needed later, it should be
    stored as a list:

       groups = []
       uniquekeys = []
       for k, g in groupby(data, keyfunc):
           groups.append(list(g))      # Store group iterator as a list
           uniquekeys.append(k)
    """
    @__graalpython__.builtin_method
    def __init__(self, iterable, key=None):
        self._marker = object()
        self._tgtkey = self._currkey = self._currvalue = self._marker
        self._currgrouper = None
        self._keyfunc = key
        self._it = iter(iterable)

    @__graalpython__.builtin_method
    def __iter__(self):
        return self

    @__graalpython__.builtin_method
    def __next__(self):
        self._currgrouper = None
        marker = self._marker
        while True:
            if self._currkey is marker:
                pass
            elif self._tgtkey is marker:
                break
            else:
                if not self._tgtkey == self._currkey:
                    break
            self._groupby_step()

        self._tgtkey = self._currkey
        grouper = _grouper(self, self._tgtkey)
        return (self._currkey, grouper)

    @__graalpython__.builtin_method
    def _groupby_step(self):
        newvalue = next(self._it)
        if self._keyfunc is None:
            newkey = newvalue
        else:
            newkey = self._keyfunc(newvalue)
        self._currvalue = newvalue
        self._currkey = newkey


class _grouper():
    @__graalpython__.builtin_method
    def __init__(self, parent, tgtkey):
        if not isinstance(parent, groupby):
            raise TypeError("incorrect usage of internal _grouper")
        parent._currgouper = self
        self._parent = parent
        self._tgtkey = tgtkey
        self._marker = parent._marker

    @__graalpython__.builtin_method
    def __iter__(self):
        return self

    @__graalpython__.builtin_method
    def __next__(self):
        gbo = self._parent
        if gbo._currgouper != self:
            raise StopIteration
        if gbo._currvalue is self._marker:
            gbo._groupby_step()
        if not (self._tgtkey == gbo._currkey):
            raise StopIteration
        r = gbo._currvalue
        gbo._currvalue = self._marker
        return r


class combinations():
    """
    combinations(iterable, r) --> combinations object

    Return successive r-length combinations of elements in the iterable.

    combinations(range(4), 3) --> (0,1,2), (0,1,3), (0,2,3), (1,2,3)
    """

    @__graalpython__.builtin_method
    def __init__(self, iterable, r):
        self.pool = tuple(iterable)
        n = len(self.pool)
        if r < 0:
            raise ValueError("r must be non-negative")
        self.indices = [i for i in range(r)]
        self.r = r
        self.last_result = None
        self.stopped = r > len(self.pool)

    @__graalpython__.builtin_method
    def get_maximum(self, i):
        return i + len(self.pool) - self.r

    @__graalpython__.builtin_method
    def max_index(self, j):
        return self.indices[j - 1] + 1

    @__graalpython__.builtin_method
    def __iter__(self):
        return self

    @__graalpython__.builtin_method
    def __next__(self):
        if self.stopped:
            raise StopIteration
        if self.last_result is None:
            # On the first pass, initialize result tuple using the indices
            result = [None] * self.r
            for i in range(self.r):
                index = self.indices[i]
                result[i] = self.pool[index]
        else:
            # Copy the previous result
            result = self.last_result[:]
            # Scan indices right-to-left until finding one that is not at its
            # maximum
            i = self.r - 1
            while i >= 0 and self.indices[i] == self.get_maximum(i):
                i -= 1

            # If i is negative, then the indices are all at their maximum value
            # and we're done
            if i < 0:
                self.stopped = True
                raise StopIteration

            # Increment the current index which we know is not at its maximum.
            # Then move back to the right setting each index to its lowest
            # possible value
            self.indices[i] += 1
            for j in range(i + 1, self.r):
                self.indices[j] = self.max_index(j)

            # Update the result for the new indices starting with i, the
            # leftmost index that changed
            for i in range(i, self.r):
                index = self.indices[i]
                elem = self.pool[index]
                result[i] = elem
        self.last_result = result
        return tuple(result)


class combinations_with_replacement(combinations):
    """
    combinations_with_replacement(iterable, r) --> combinations_with_replacement object

    Return successive r-length combinations of elements in the iterable
    allowing individual elements to have successive repeats.
    combinations_with_replacement('ABC', 2) --> AA AB AC BB BC CC
    """
    @__graalpython__.builtin_method
    def __init__(self, iterable, r):
        pool = list(iterable)
        if r < 0:
            raise ValueError("r must be non-negative")
        super().__init__(pool, r)
        self.indices = [0] * r
        self.stopped = len(pool) == 0 and r > 0

    @__graalpython__.builtin_method
    def get_maximum(self, i):
        return len(self.pool) - 1

    @__graalpython__.builtin_method
    def max_index(self, j):
        return self.indices[j - 1]


class zip_longest():
    """
    zip_longest(iter1 [,iter2 [...]], [fillvalue=None]) --> zip_longest object

    Return a zip_longest object whose .next() method returns a tuple where
    the i-th element comes from the i-th iterable argument.  The .next()
    method continues until the longest iterable in the argument sequence
    is exhausted and then it raises StopIteration.  When the shorter iterables
    are exhausted, the fillvalue is substituted in their place.  The fillvalue
    defaults to None or can be specified by a keyword argument.
    """

    @__graalpython__.builtin_method
    def __new__(subtype, *args, fillvalue=None):
        self = object.__new__(subtype)
        self.fillvalue = fillvalue
        self.tuplesize = len(args)
        self.numactive = len(args)
        self.ittuple = [iter(arg) for arg in args]
        return self

    @__graalpython__.builtin_method
    def __iter__(self):
        return self

    @__graalpython__.builtin_method
    def __next__(self):
        if not self.tuplesize:
            raise StopIteration
        if not self.numactive:
            raise StopIteration
        result = [None] * self.tuplesize
        for idx, it in enumerate(self.ittuple):
            if it is None:
                item = self.fillvalue
            else:
                try:
                    item = next(it)
                except StopIteration:
                    self.numactive -= 1
                    if self.numactive == 0:
                        raise StopIteration
                    else:
                        item = self.fillvalue
                        self.ittuple[idx] = None
                except:
                    self.numactive = 0
                    raise
            result[idx] = item
        return tuple(result)

    @__graalpython__.builtin_method
    def __reduce__(self):
        args = []
        for elem in self.ittuple:
            args.append(elem if elem is not None else tuple())
        return type(self), tuple(args), self.fillvalue

    @__graalpython__.builtin_method
    def __setstate__(self, state):
        self.fillvalue = state


class cycle():
    """
    Make an iterator returning elements from the iterable and
    saving a copy of each. When the iterable is exhausted, return
    elements from the saved copy. Repeats indefinitely.

    Equivalent to :

    def cycle(iterable):
        saved = []
        for element in iterable:
            yield element
            saved.append(element)
        while saved:
            for element in saved:
                yield element
    """

    @__graalpython__.builtin_method
    def __init__(self, iterable):
        self.saved = []
        self.iterable = iter(iterable)
        self.index = 0
        self.firstpass = False

    @__graalpython__.builtin_method
    def __iter__(self):
        return self

    @__graalpython__.builtin_method
    def __next__(self):
        if self.iterable:
            try:
                obj = next(self.iterable)
            except StopIteration:
                self.iterable = None
            else:
                if not self.firstpass:
                    self.saved.append(obj)
                return obj
        if not self.saved:
            raise StopIteration
        obj = self.saved[self.index]
        self.index += 1
        if self.index >= len(self.saved):
            self.index = 0
        return obj

    @__graalpython__.builtin_method
    def __reduce__(self):
        if self.iterable is None:
            it = iter(self.saved)
            if self.index:
                it.__setstate__(self.index)
            return type(self), (it,), (self.saved, True)
        return type(self), (self.iterable,), (self.saved, self.firstpass)

    @__graalpython__.builtin_method
    def __setstate__(self, state):
        if (not isinstance(state, tuple) or
            len(state) != 2 or
            not isinstance(state[0], list) or
            not isinstance(state[1], int)):
            raise TypeError("invalid state tuple")
        self.saved = state[0]
        self.firstpass = state[1]
        self.index = 0


class compress():
    """Make an iterator that filters elements from *data* returning
   only those that have a corresponding element in *selectors* that evaluates to
   ``True``.  Stops when either the *data* or *selectors* iterables has been
   exhausted.
   Equivalent to::

       def compress(data, selectors):
           # compress('ABCDEF', [1,0,1,0,1,1]) --> A C E F
           return (d for d, s in zip(data, selectors) if s)
    """
    @__graalpython__.builtin_method
    def __init__(self, data, selectors):
        self.data = iter(data)
        self.selectors = iter(selectors)

    @__graalpython__.builtin_method
    def __iter__(self):
        return self

    @__graalpython__.builtin_method
    def __next__(self):
        # No need to check for StopIteration since either data or selectors will
        # raise this. The shortest one stops first.
        while True:
            next_item = next(self.data)
            next_selector = next(self.selectors)
            if next_selector:
                return next_item

    @__graalpython__.builtin_method
    def __reduce__(self):
        return (type(self), (self.data, self.selectors))


class _tee_dataobject:
    LINKCELLS = 128

    @__graalpython__.builtin_method
    def __init__(self, it, values=None, nxt=None):
        self.it = it
        if values:
            self.values = values
            self.numread = len(values)
            if self.numread == _tee_dataobject.LINKCELLS:
                self.nextlink = nxt
                if nxt and not isinstance(nxt, _tee_dataobject):
                    raise ValueError("_tee_dataobject next link must be a _tee_dataobject")
            elif self.numread > _tee_dataobject.LINKCELLS:
                raise ValueError(f"_tee_dataobject should nove have more than {_tee_dataobject.LINKCELLS} links")
            elif nxt is not None:
                raise ValueError("_tee_dataobject shouldn't have a next if not full")
        else:
            self.values = []
            self.numread = 0
        self.running = False
        self.nextlink = nxt

    @__graalpython__.builtin_method
    def _jumplink(self):
        if not self.nextlink:
            self.nextlink = _tee_dataobject(self.it)
        return self.nextlink

    @__graalpython__.builtin_method
    def _getitem(self, i):
        assert i < _tee_dataobject.LINKCELLS
        if i < self.numread:
            return self.values[i]
        else:
            assert i == self.numread
            if self.running:
                raise RuntimeError("cannot re-enter the tee iterator")
            self.running = True
            try:
                value = next(self.it)
            finally:
                self.running = False
            self.numread += 1
            self.values.append(value)
            return value

    @__graalpython__.builtin_method
    def __reduce__(self):
        values = self.values[:self.numread]
        return type(self), (self.it, values, self.nextlink)


class _tee:
    # This uses a linked list of fixed size lists where
    # the last item in the fixed size list is a link to
    # another fixed size list. Once all _tee instances have
    # traversed given fixed size list, it'll become a garbage
    # to be collected
    @__graalpython__.builtin_method
    def __new__(cls, iterable):
        it = iter(iterable)
        if isinstance(it, _tee):
            return it.__copy__()
        else:
            to = object.__new__(_tee)
            to.dataobj = _tee_dataobject(it)
            to.index = 0
        return to

    @__graalpython__.builtin_method
    def __iter__(self):
        return self

    @__graalpython__.builtin_method
    def __next__(self):
        if self.index >= _tee_dataobject.LINKCELLS:
            self.dataobj = self.dataobj._jumplink()
            self.index = 0
        value = self.dataobj._getitem(self.index)
        self.index += 1
        return value

    @__graalpython__.builtin_method
    def __reduce__(self):
        return type(self), ((),), (self.dataobj, self.index)

    @__graalpython__.builtin_method
    def __setstate__(self, state):
        if not isinstance(state, tuple) or len(state) != 2:
            raise TypeError("state is not a 2-tuple")
        if not isinstance(state[0], _tee_dataobject):
            raise TypeError("state is not a _tee_dataobject")
        self.dataobj = state[0]
        if state[1] < 0 or state[1] > _tee_dataobject.LINKCELLS:
            raise ValueError("Index out of range")
        self.index = int(state[1])

    @__graalpython__.builtin_method
    def __copy__(self):
        to = object.__new__(_tee)
        to.dataobj = self.dataobj
        to.index = self.index
        return to


def tee(iterable, n=2):
    if not isinstance(n, int):
        raise TypeError()
    if n < 0:
        raise ValueError("n must be >=0")
    if n == 0:
        return tuple()
    # if the iterator can be copied, use that instead of _tee
    # note: this works for _tee itself
    it = iter(iterable)
    copy = getattr(it, "__copy__", None)
    if not callable(copy):
        it = _tee(it)
    return tuple([it] + [it.__copy__() for i in range(1, n)])
