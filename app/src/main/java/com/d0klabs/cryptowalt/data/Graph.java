package com.d0klabs.cryptowalt.data;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Graph {
    private Graph.NodeMap nodes = new Graph.NodeMap();
    private Graph.NodeList preOrder = null;
    private Graph.NodeList postOrder = null;
    private Collection roots = null;
    private Collection revRoots = null;
    protected int rootEdgeModCount = 0;
    protected int revRootEdgeModCount = 0;
    protected int nodeModCount = 0;
    protected int edgeModCount = 0;
    protected int removingNode = 0;
    protected int removingEdge = 0;

    public Graph() {
    }

    public Collection roots() {
        if (this.roots == null || this.rootEdgeModCount != this.edgeModCount) {
            this.rootEdgeModCount = this.edgeModCount;
            this.roots = new ArrayList();
            this.buildRootList(this.roots, false);
        }

        return this.roots;
    }

    public Collection reverseRoots() {
        if (this.roots == null || this.revRootEdgeModCount != this.edgeModCount) {
            this.revRootEdgeModCount = this.edgeModCount;
            this.revRoots = new ArrayList();
            this.buildRootList(this.revRoots, true);
        }

        return this.revRoots;
    }

    private void buildRootList(Collection c, boolean reverse) {
        HashSet visited = new HashSet(this.nodes.size() * 2);
        ArrayList stack = new ArrayList();
        Iterator iter = this.nodes.values().iterator();

        while(true) {
            GraphNode node;
            do {
                if (!iter.hasNext()) {
                    return;
                }

                node = (GraphNode)iter.next();
            } while(visited.contains(node));

            visited.add(node);
            stack.add(node);

            while(!stack.isEmpty()) {
                GraphNode v = (GraphNode)stack.remove(stack.size() - 1);
                boolean pushed = false;
                Iterator preds = reverse ? v.succs.iterator() : v.preds.iterator();

                while(preds.hasNext()) {
                    GraphNode w = (GraphNode)preds.next();
                    if (!visited.contains(w)) {
                        visited.add(w);
                        stack.add(w);
                        pushed = true;
                    }
                }

                if (!pushed) {
                    c.add(v);
                }
            }
        }
    }

    public Collection succs(GraphNode v) {
        return new Graph.EdgeSet(v, v.succs);
    }

    public Collection preds(GraphNode v) {
        return new Graph.EdgeSet(v, v.preds);
    }

    public boolean isAncestorToDescendent(GraphNode v, GraphNode w) {
        return this.preOrderIndex(v) <= this.preOrderIndex(w) && this.postOrderIndex(w) <= this.postOrderIndex(v);
    }

    public int preOrderIndex(GraphNode node) {
        if (this.preOrder == null || this.edgeModCount != this.preOrder.edgeModCount) {
            this.buildLists();
        }

        return node.preOrderIndex();
    }

    public int postOrderIndex(GraphNode node) {
        if (this.postOrder == null || this.edgeModCount != this.postOrder.edgeModCount) {
            this.buildLists();
        }

        return node.postOrderIndex();
    }

    public List preOrder() {
        if (this.preOrder == null || this.edgeModCount != this.preOrder.edgeModCount) {
            this.buildLists();
        }

        return this.preOrder;
    }

    public List postOrder() {
        if (this.postOrder == null || this.edgeModCount != this.postOrder.edgeModCount) {
            this.buildLists();
        }

        return this.postOrder;
    }

    private void buildLists() {
        Iterator iter = this.roots().iterator();
        this.preOrder = new Graph.NodeList();
        this.postOrder = new Graph.NodeList();
        HashSet visited = new HashSet();

        GraphNode node;
        while(iter.hasNext()) {
            node = (GraphNode)iter.next();
            this.number(node, visited);
        }

        iter = this.nodes.values().iterator();

        while(iter.hasNext()) {
            node = (GraphNode)iter.next();
            if (!visited.contains(node)) {
                node.setPreOrderIndex(-1);
                node.setPostOrderIndex(-1);
            }
        }

    }

    public void removeUnreachable() {
        if (this.preOrder == null || this.edgeModCount != this.preOrder.edgeModCount) {
            this.buildLists();
        }

        Iterator iter = this.nodes.entrySet().iterator();

        while(iter.hasNext()) {
            Map.Entry e = (Map.Entry)iter.next();
            GraphNode v = (GraphNode)e.getValue();
            if (v.preOrderIndex() == -1) {
                iter.remove();
            }
        }

    }

    private void number(GraphNode node, Set visited) {
        visited.add(node);
        node.setPreOrderIndex(this.preOrder.size());
        this.preOrder.addNode(node);
        Iterator iter = this.succs(node).iterator();

        while(iter.hasNext()) {
            GraphNode succ = (GraphNode)iter.next();
            if (!visited.contains(succ)) {
                this.number(succ, visited);
            }
        }

        node.setPostOrderIndex(this.postOrder.size());
        this.postOrder.addNode(node);
    }

    public void addNode(Object key, GraphNode node) {
        this.nodes.putNodeInMap(key, node);
        this.preOrder = null;
        this.postOrder = null;
        ++this.nodeModCount;
        ++this.edgeModCount;
    }

    public GraphNode getNode(Object key) {
        return (GraphNode)this.nodes.get(key);
    } //TODO: it will return Graphnode with its index of tree

    public Set keySet() {
        return this.nodes.keySet();
    }

    public void removeNode(Object key) {
        GraphNode node = this.getNode(key);
        this.succs(node).clear();
        this.preds(node).clear();
        if (this.removingNode == 0) {
            this.nodes.removeNodeFromMap(key);
        } else if (this.removingNode != 1) {
            throw new RuntimeException();
        }

        this.preOrder = null;
        this.postOrder = null;
        ++this.nodeModCount;
        ++this.edgeModCount;
    } //TODO: remove Node with its connections

    public void addEdge(GraphNode v, GraphNode w) {
        this.succs(v).add(w);
        ++this.edgeModCount;
    } // What is edge?

    public void removeEdge(GraphNode v, GraphNode w) {
        if (this.removingEdge == 0) {
            this.succs(v).remove(w);
        } else if (this.removingEdge != 1) {
            throw new RuntimeException();
        }

        ++this.edgeModCount;
    }

    public String toString() {
        String s = "";

        for(Iterator iter = this.nodes.values().iterator(); iter.hasNext(); s = s + "]\n") {
            GraphNode node = (GraphNode)iter.next();
            s = s + "[" + node;
            s = s + " succs = " + node.succs();
            s = s + " preds = " + node.preds();
        }

        return s;
    } // TODO: rework!

    public boolean hasNode(GraphNode v) {
        return this.nodes.containsValue(v);
    }

    public boolean hasEdge(GraphNode v, GraphNode w) {
        return this.succs(v).contains(w);
    }

    public Collection nodes() {
        return this.nodes.values();
    }

    public int size() {
        return this.nodes.size();
    }

    class EdgeSet extends AbstractSet {
        GraphNode node;
        Set set;
        int nodeModCount;

        public EdgeSet(GraphNode node, Set set) {
            this.node = node;
            this.set = set;
            this.nodeModCount = Graph.this.nodeModCount;
        }

        public int size() {
            if (this.nodeModCount != Graph.this.nodeModCount) {
                throw new ConcurrentModificationException();
            } else {
                return this.set.size();
            }
        }

        public boolean retainAll(Collection c) {
            return super.retainAll(new ArrayList(c));
        }

        public boolean removeAll(Collection c) {
            return super.removeAll(new ArrayList(c));
        }

        public boolean addAll(Collection c) {
            return super.addAll(new ArrayList(c));
        }

        public boolean add(Object a) {
            if (this.nodeModCount != Graph.this.nodeModCount) {
                throw new ConcurrentModificationException();
            } else {
                GraphNode v = (GraphNode)a;
                if (this.set.add(v)) {
                    ++Graph.this.edgeModCount;
                    if (this.set == this.node.succs) {
                        v.preds.add(this.node);
                    } else {
                        v.succs.add(this.node);
                    }

                    return true;
                } else {
                    return false;
                }
            }
        }

        public boolean remove(Object a) {
            if (this.nodeModCount != Graph.this.nodeModCount) {
                throw new ConcurrentModificationException();
            } else {
                GraphNode v = (GraphNode)a;
                if (this.set.contains(v)) {
                    ++Graph.this.edgeModCount;
                    if (this.set == this.node.succs) {
                        ++Graph.this.removingEdge;
                        Graph.this.removeEdge(this.node, v);
                        --Graph.this.removingEdge;
                        v.preds.remove(this.node);
                    } else {
                        ++Graph.this.removingEdge;
                        Graph.this.removeEdge(v, this.node);
                        --Graph.this.removingEdge;
                        v.succs.remove(this.node);
                    }

                    this.set.remove(v);
                    return true;
                } else {
                    return false;
                }
            }
        }

        public boolean contains(Object a) {
            if (this.nodeModCount != Graph.this.nodeModCount) {
                throw new ConcurrentModificationException();
            } else {
                return a instanceof GraphNode ? this.set.contains(a) : false;
            }
        }

        public void clear() {
            if (this.nodeModCount != Graph.this.nodeModCount) {
                throw new ConcurrentModificationException();
            } else {
                Iterator iter = this.set.iterator();

                while(iter.hasNext()) {
                    GraphNode v = (GraphNode)iter.next();
                    if (this.set == this.node.succs) {
                        ++Graph.this.removingEdge;
                        Graph.this.removeEdge(this.node, v);
                        --Graph.this.removingEdge;
                        v.preds.remove(this.node);
                    } else {
                        ++Graph.this.removingEdge;
                        Graph.this.removeEdge(v, this.node);
                        --Graph.this.removingEdge;
                        v.succs.remove(this.node);
                    }
                }

                ++Graph.this.edgeModCount;
                this.set.clear();
            }
        }

        public Iterator iterator() {
            if (this.nodeModCount != Graph.this.nodeModCount) {
                throw new ConcurrentModificationException();
            } else {
                final Iterator iter = this.set.iterator();
                return new Iterator() {
                    GraphNode last;
                    int edgeModCount;
                    int nodeModCount;

                    {
                        this.edgeModCount = Graph.this.edgeModCount;
                        this.nodeModCount = EdgeSet.this.nodeModCount;
                    }

                    public boolean hasNext() {
                        if (this.nodeModCount != Graph.this.nodeModCount) {
                            throw new ConcurrentModificationException();
                        } else if (this.edgeModCount != Graph.this.edgeModCount) {
                            throw new ConcurrentModificationException();
                        } else {
                            return iter.hasNext();
                        }
                    }

                    public Object next() {
                        if (this.nodeModCount != Graph.this.nodeModCount) {
                            throw new ConcurrentModificationException();
                        } else if (this.edgeModCount != Graph.this.edgeModCount) {
                            throw new ConcurrentModificationException();
                        } else {
                            this.last = (GraphNode)iter.next();
                            return this.last;
                        }
                    }

                    public void remove() {
                        if (this.nodeModCount != Graph.this.nodeModCount) {
                            throw new ConcurrentModificationException();
                        } else if (this.edgeModCount != Graph.this.edgeModCount) {
                            throw new ConcurrentModificationException();
                        } else {
                            if (EdgeSet.this.set == EdgeSet.this.node.succs) {
                                ++Graph.this.removingEdge;
                                Graph.this.removeEdge(EdgeSet.this.node, this.last);
                                --Graph.this.removingEdge;
                                this.last.preds.remove(EdgeSet.this.node);
                            } else {
                                ++Graph.this.removingEdge;
                                Graph.this.removeEdge(this.last, EdgeSet.this.node);
                                --Graph.this.removingEdge;
                                this.last.succs.remove(EdgeSet.this.node);
                            }

                            ++Graph.this.edgeModCount;
                            this.edgeModCount = Graph.this.edgeModCount;
                            iter.remove();
                        }
                    }
                };
            }
        }
    }

    class NodeList extends ArrayList implements List {
        int edgeModCount;

        NodeList() {
            super(Graph.this.size());
            this.edgeModCount = Graph.this.edgeModCount;
        }

        boolean addNode(GraphNode a) {
            return super.add(a);
        }

        public void clear() {
            throw new UnsupportedOperationException();
        } //TODO: remove all in list and check zero status

        public boolean add(Object a) {
            throw new UnsupportedOperationException();
        } //TODO: wtf? and how to set new nodes?

        public boolean remove(Object a) {
            throw new UnsupportedOperationException();
        } //TODO: nice))

        public int indexOf(Object a) {
            if (this.edgeModCount != Graph.this.edgeModCount) {
                throw new ConcurrentModificationException();
            } else {
                GraphNode v = (GraphNode)a;
                if (this == Graph.this.preOrder) {
                    return v.preOrderIndex();
                } else {
                    return this == Graph.this.postOrder ? v.postOrderIndex() : super.indexOf(a);
                }
            }
        }

        public int indexOf(Object a, int index) {
            int i = this.indexOf(a);
            return i >= index ? i : -1;
        }

        public int lastIndexOf(Object a) {
            if (this.edgeModCount != Graph.this.edgeModCount) {
                throw new ConcurrentModificationException();
            } else {
                GraphNode v = (GraphNode)a;
                if (this == Graph.this.preOrder) {
                    return v.preOrderIndex();
                } else {
                    return this == Graph.this.postOrder ? v.postOrderIndex() : super.lastIndexOf(a);
                }
            }
        }

        public int lastIndexOf(Object a, int index) {
            int i = this.indexOf(a);
            return i <= index ? i : -1;
        }

        public Iterator iterator() {
            if (Graph.this.edgeModCount != this.edgeModCount) {
                throw new ConcurrentModificationException();
            } else {
                final Iterator iter = super.iterator();
                return new Iterator() {
                    int edgeModCount;
                    Object last;

                    {
                        this.edgeModCount = NodeList.this.edgeModCount;
                    }

                    public boolean hasNext() {
                        if (Graph.this.edgeModCount != this.edgeModCount) {
                            throw new ConcurrentModificationException();
                        } else {
                            return iter.hasNext();
                        }
                    }

                    public Object next() {
                        if (Graph.this.edgeModCount != this.edgeModCount) {
                            throw new ConcurrentModificationException();
                        } else {
                            this.last = iter.next();
                            return this.last;
                        }
                    }

                    public void remove() {
                        throw new UnsupportedOperationException();
                    }
                };
            }
        }
    }

    class NodeMap extends AbstractMap {
        HashMap map = new HashMap();

        NodeMap() {
        }

        void removeNodeFromMap(Object key) {
            this.map.remove(key);
        } //TODO: oh! value saved!

        void putNodeInMap(Object key, Object value) {
            this.map.put(key, value);
        } //TODO:f@! #! Node by that where free from other connections. Where that prog? I would like to see his eyes)))

        public Object remove(Object key) {
            GraphNode v = (GraphNode)this.map.get(key);
            if (v != null) {
                Graph.this.removeNode(v);
            }

            return v;
        }//TODO: remove value and index, remove connections

        public Object put(Object key, Object value) {
            GraphNode v = (GraphNode)this.remove(key);
            Graph.this.addNode(key, (GraphNode)value);
            return v;
        } //TODO: & return index of current v

        public void clear() {
            Iterator iter = this.entrySet().iterator();

            while(iter.hasNext()) {
                Entry e = (Entry)iter.next();
                ++Graph.this.removingNode;
                Graph.this.removeNode(e.getKey());
                --Graph.this.removingNode;
                iter.remove();
            }

        }

        public Set entrySet() {
            final Collection entries = this.map.entrySet();
            return new AbstractSet() {
                public int size() {
                    return entries.size();
                }

                public boolean contains(Object a) {
                    return entries.contains(a);
                }

                public boolean remove(Object a) {
                    Entry e = (Entry)a;
                    ++Graph.this.removingNode;
                    Graph.this.removeNode(e.getKey());
                    --Graph.this.removingNode;
                    return entries.remove(a);
                }

                public void clear() {
                    Iterator iter = entries.iterator();

                    while(iter.hasNext()) {
                        Entry e = (Entry)iter.next();
                        ++Graph.this.removingNode;
                        Graph.this.removeNode(e.getKey());
                        --Graph.this.removingNode;
                        iter.remove();
                    }

                }

                public Iterator iterator() {
                    final Iterator iter = entries.iterator();
                    return new Iterator() {
                        int nodeModCount;
                        Entry last;

                        {
                            this.nodeModCount = Graph.this.nodeModCount;
                        }

                        public boolean hasNext() {
                            if (this.nodeModCount != Graph.this.nodeModCount) {
                                throw new ConcurrentModificationException();
                            } else {
                                return iter.hasNext();
                            }
                        }

                        public Object next() {
                            if (this.nodeModCount != Graph.this.nodeModCount) {
                                throw new ConcurrentModificationException();
                            } else {
                                this.last = (Entry)iter.next();
                                return this.last;
                            }
                        }

                        public void remove() {
                            if (this.nodeModCount != Graph.this.nodeModCount) {
                                throw new ConcurrentModificationException();
                            } else {
                                ++Graph.this.removingNode;
                                Graph.this.removeNode(this.last.getKey());
                                --Graph.this.removingNode;
                                iter.remove();
                                this.nodeModCount = Graph.this.nodeModCount;
                            }
                        }
                    };
                }
            };
        }
    }
}
