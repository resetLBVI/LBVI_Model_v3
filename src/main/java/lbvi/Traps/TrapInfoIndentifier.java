package lbvi.Traps;

/*
Initial Trap information:
"POINT_X"    "POINT_Y"    "TrapID"     "Year"
Open/close dates are model-wide parameters (mpOpenDate, mpCloseDate), not per-trap columns.
 */

public class TrapInfoIndentifier {
    public final double trapPOINT_X;
    public final double trapPOINT_Y;
    public final int TrapID;
    public final int year;

    public TrapInfoIndentifier(double POINT_X, double POINT_Y, int trapID, int year) {
        this.trapPOINT_X = POINT_X;
        this.trapPOINT_Y = POINT_Y;
        TrapID = trapID;
        this.year = year;
    }
}
