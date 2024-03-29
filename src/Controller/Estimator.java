package Controller;

import View.MainClass;
import model.Node;
import model.Query;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Vector;

public class Estimator {
    private Map<String, Integer> nbrLines = new HashMap<String, Integer>();
    private Map<String, Integer> FBM = new HashMap<String, Integer>();
    private Node tree;
    Query query;
    private final double TempsTrans = 0.1;
    private final double TempsPosDébut = 1;
    private final double TR = 0.8;
    private final double M = 50;

    public Estimator(Node tree, Query query) {
        this.tree = tree;
        this.query = query;

        for (String table : query.getTables()){
            nbrLines.put(table, MainClass.catalog.getStatsTable(table, 1));
            FBM.put(table, MainClass.catalog.getStatsTable(table, 4));
        }
        /*System.out.println(nbrLines);
        System.out.println(FBM);*/
    }


    public void estimate(double[] cout){
        estimate(tree.getLeftChild(), cout);
        //we should move this section later !!!
        //double ct = calculCoutTot(tree, cout);
        tree.setCout(1.1);
        cout[0] = Math.round(cout[0] + 1.1 + 0.5);
        cout[1] = Math.round(cout[1] + 1.1 + 0.5);
        //
        //return Math.round(ct + 0.5);
    }

    private int estimate(Node N, double[] cout){
        int nbrLine = 0, left, right = 0;

        if (N.getLeftChild() == null)
            return nbrLines.get(N.getExpression());

        left = estimate(N.getLeftChild(), cout);

        //** Binary operator
        if (N.getRightChild() != null){
            right = estimate(N.getRightChild(), cout);

            switch (N.getName()){
                case "BIB" :
                    nbrLine = BIB(N,left,right);
                    break;
                case "BII" :
                    nbrLine = BII(N,left,right);
                    break;
                case "JTF" :
                    nbrLine = JTF(N,left,right);
                    break;
                case "JH" :
                    nbrLine = JH(N,left,right);
                    break;
                case "PJ" :
                    nbrLine = PJ(N,left,right);
            }

            if(cout[0] <= N.getCout())
                cout[0] = N.getCout();
            else {
                Random random = new Random();
                cout[0] += random.nextDouble(N.getCout() - 0 + 1) + 0;
            }

        } else {
            switch (N.getName()){
                case "FS" :
                    nbrLine = FS(N,left);
                    break;
                case "IS" :
                    nbrLine = IS(N,left);
                    break;
                case "HS" :
                    nbrLine = HS(N,left);
            }
        }

        cout[1] += N.getCout() + 1.1;

        return nbrLine;
    }

    private double calculCoutTot(Node node , double[] pipeline_cout ){
        double left=0,right =0;
        if(node.getLeftChild() == null)
            return 0;

        left = calculCoutTot(node.getLeftChild(),pipeline_cout);
        if(node.getRightChild() != null ) {
            right = calculCoutTot(node.getRightChild(), pipeline_cout);
            if(pipeline_cout[0] < node.getCout())
                pipeline_cout[0] = node.getCout();
        }
        if(node.getLeftChild().getLeftChild() != null)
            return left + right + node.getCout() + 1.1;
        return left + right + node.getCout();

    }

    //Jointure Algorithmes
    public int BIB(Node node , int left , int right){

        Vector<Decomposer.MyPair<String,String>> pairs = Decomposer.joinSplit(node.getExpression());
        String tableL, columnL, tableR, columnR;
        tableL = query.getAliasTable(pairs.get(0).getSecond());
        columnL = pairs.get(0).getFirst();
        tableR = query.getAliasTable(pairs.get(1).getSecond());
        columnR = pairs.get(1).getFirst();

        double Bl = left/FBM.get(tableL);
        double Br = right/FBM.get(tableR);

        node.setCout(Math.round(Bl*((TempsTrans+TempsPosDébut)+(Br*TempsTrans)+TempsPosDébut) + 0.5));
        //System.out.println(node.getCout());
        return (int) ((left + right)*0.7);
    }

    public int BII(Node node , int left , int right){
        Vector<Decomposer.MyPair<String,String>> pairs = Decomposer.joinSplit(node.getExpression());
        String tableL, columnL, tableR, columnR;
        tableL = query.getAliasTable(pairs.get(0).getSecond());
        columnL = pairs.get(0).getFirst();
        tableR = query.getAliasTable(pairs.get(1).getSecond());
        columnR = pairs.get(1).getFirst();

        double Bl = left/FBM.get(tableL);
        //
        int orderMoyen = MainClass.catalog.getColumnDesc(tableR, columnR, 2);
        double hauteur, Tsecondaire = 0;
        hauteur = (double) Math.round(Math.log(right) / Math.log(orderMoyen) + 0.5);

        if(MainClass.catalog.isPrimaryKey(tableR, columnR)){
            Tsecondaire = (hauteur + 1) * (TempsTrans + TempsPosDébut);
        } else {
            double sel = right / MainClass.catalog.getColumnCard(columnR, tableR);
            Tsecondaire = Math.round(((hauteur-1) + sel + sel/orderMoyen) * (TempsTrans + TempsPosDébut) + 0.5);
        }
        //
        node.setCout( Math.round(Bl * (TempsTrans + TempsPosDébut) + (left * Tsecondaire) +0.5));
        //System.out.println(node.getCout());
        //System.out.println(Tsecondaire);

        return (int) ((left + right)*0.7);
    }

    public int JTF(Node node, int left, int right){

        Vector<Decomposer.MyPair<String,String>> pairs = Decomposer.joinSplit(node.getExpression());
        String tableL, columnL, tableR, columnR;
        tableL = query.getAliasTable(pairs.get(0).getSecond());
        columnL = pairs.get(0).getFirst();
        tableR = query.getAliasTable(pairs.get(1).getSecond());
        columnR = pairs.get(1).getFirst();

        double Bl,Br,TempEsL,TempEsR,cout;

        Bl = left / FBM.get(tableL);
        Br = right / FBM.get(tableR);
        TempEsL = 2*((Bl/M)*TempsPosDébut + Bl*TempsTrans) + Bl*(2*(Math.log(Bl/M)/Math.log(M-1)) - 1)*(TempsTrans+TempsPosDébut);
        TempEsR = 2*((Br/M)*TempsPosDébut + Br*TempsTrans) + Br*(2*(Math.log(Br/M)/Math.log(M-1)) - 1)*(TempsTrans+TempsPosDébut);

        cout = TempEsL+TempEsR+2*(Bl+Br)*(TempsTrans+TempsPosDébut);
        node.setCout(Math.round(cout+0.5));
        //System.out.println(cout);
        return (int) ((left + right)*0.7);
    }

    public int JH(Node node, int left, int right){

        Vector<Decomposer.MyPair<String,String>> pairs = Decomposer.joinSplit(node.getExpression());
        String tableL, tableR;
        tableL = query.getAliasTable(pairs.get(0).getSecond());
        tableR = query.getAliasTable(pairs.get(1).getSecond());

        double Bl,Br,Bal_l,Bal_r,cout;

        Bl = (double)left / FBM.get(tableL);
        Br = (double)right / FBM.get(tableR);
        Bal_l = Bl * TempsTrans;
        Bal_r = Br * TempsTrans;

        //cout = Bal_l+Bal_r+2*(Bl+Br)*(TempsTrans+TempsPosDébut);
        cout = Bal_l+Bal_r+2*(Bl+Br)*(TempsTrans+TempsPosDébut);
        node.setCout(Math.round(cout+0.5));
        //System.out.println(cout);
        return (int) ((left + right)*0.7);
    }

    public int PJ(Node node, int left, int right){

        Vector<Decomposer.MyPair<String,String>> pairs = Decomposer.joinSplit(node.getExpression());
        String tableL, tableR;
        tableL = query.getAliasTable(pairs.get(0).getSecond());
        tableR = query.getAliasTable(pairs.get(1).getSecond());

        double Bl,Br,Bal_l,Bal_r, cout;

        Bl = left / FBM.get(tableL);
        Br = right / FBM.get(tableR);
        Bal_l = Bl * TempsTrans;
        Bal_r = Br * TempsTrans;

        //cout = Bal_l+Bal_r+2*(Bl+Br)*(TempsTrans+TempsPosDébut);
        cout = Bal_l+Bal_r;
        node.setCout(Math.round(cout+0.5));
        if(tableL.equals("EMPLOYER") || tableR.equals("EMPLOYER"))
            node.setCout(1000000);
        //System.out.println(cout);
        return (int) ((left + right)*0.7);
    }
    //Selection Algorithmes

    public int FS(Node node , int nbrLigne){

        Decomposer.MyPair<String, String> pair = Decomposer.selectionSplit(node.getExpression());
        double cout = (nbrLigne/FBM.get(query.getAliasTable(pair.getSecond())) * TempsTrans);
        //System.out.println("Column : " + pair.getFirst() + "  Table : " + pair.getSecond());
        node.setCout(cout);
        //System.out.println("Cout = " + cout);
        return (int) (nbrLigne*0.7);

    }

    public int IS(Node node , int nbrLigne){

        String table, column;
        Decomposer.MyPair<String, String> pair = Decomposer.selectionSplit(node.getExpression());
        table = query.getAliasTable(pair.getSecond());
        column = pair.getFirst();
        int orderMoyen = MainClass.catalog.getColumnDesc(table, column, 2);
        double hauteur, cout = 0;
        hauteur = (double) Math.round(Math.log(nbrLigne) / Math.log(orderMoyen) + 0.5);



        if(MainClass.catalog.isPrimaryKey(table, column)){

            cout = (hauteur + 1) * (TempsTrans + TempsPosDébut);

        } else {

            double sel = nbrLigne / MainClass.catalog.getColumnCard(column, table);
            cout = Math.round(((hauteur -1) + sel + sel/orderMoyen) * (TempsTrans + TempsPosDébut) + 0.5);

        }

        node.setCout(cout);
        //System.out.println("Cout = " + cout);

        return (int) (nbrLigne*0.7);
    }

    public int HS(Node node , int nbrLigne){

        String table, column;
        double FB, TH, cout = 0;
        Decomposer.MyPair<String, String> pair = Decomposer.selectionSplit(node.getExpression());
        table = query.getAliasTable(pair.getSecond());
        column = pair.getFirst();
        int NL = nbrLigne;

        FB = FBM.get(table) * TR;
        TH = NL / FB;
        cout = (NL / (TH)) * (TempsTrans + TempsPosDébut);
        node.setCout(cout);
        //System.out.println("Cout = " + cout);
        return (int) (nbrLigne*0.7);
    }

}
