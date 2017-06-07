package cn.edu.pku.sei.plde.ACS.jdtVisitor;

/**
 * Created by nightwish on 17-6-7.
 */
import java.io.File;

import org.eclipse.jdt.core.dom.*;

public class LoopVisitor extends ASTVisitor{

    private CompilationUnit cu;
    private int errLine = -1;
    private boolean inWhileStmt = false;
    private boolean inForStmt = false;
    private boolean inDoStmt = false;

    public boolean isInWhileStmt() {
        return inWhileStmt;
    }

    public boolean isInForStmt() {
        return inForStmt;
    }

    public boolean isInDoStmt() {
        return inDoStmt;
    }

    public LoopVisitor(CompilationUnit cu, int line){
        this.cu = cu;
        errLine = line;
    }


    private boolean inScope(ASTNode node){
        int begin = cu.getLineNumber(node.getStartPosition());
        int end = cu.getLineNumber(node.getStartPosition() + node.getLength());

        if(errLine >= begin && errLine <= end){
            return true;
        }
        return false;
    }

    @Override
    public boolean visit(DoStatement node) {
        if(inScope(node)){
            inDoStmt = true;
        }
        return super.visit(node);
    }
    @Override
    public boolean visit(EnhancedForStatement node) {
        if(inScope(node)){
            inForStmt = true;
        }
        return super.visit(node);
    }
    @Override
    public boolean visit(ForStatement node) {
        if(inScope(node)){
            inForStmt = true;
        }
        return super.visit(node);
    }
    @Override
    public boolean visit(WhileStatement node) {
        if(inScope(node)){
            inWhileStmt = true;
        }
        return super.visit(node);
    }

}

