package io.openaev.config;

import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.update.Update;
import org.junit.jupiter.api.Test;

public class JSQLParserSubQueryTest {
  @Test
  public void test() throws Exception {
    String filterSql =
        "current_setting('app.current_tenants', true) IS NULL OR current_setting('app.current_tenants', true) = '' OR d.tenant_id = ANY(string_to_array(current_setting('app.current_tenants', true), ','))";

    // SELECT
    String query = "SELECT * FROM document d WHERE d.name = 'test' OR d.status = 'open'";
    Select stmt = (Select) CCJSqlParserUtil.parse(query);
    PlainSelect select = (PlainSelect) stmt;

    String subSelectSql = "(SELECT * FROM document d WHERE " + filterSql + ") AS d";
    String dummySql = "SELECT * FROM " + subSelectSql;
    net.sf.jsqlparser.statement.Statement dummyStmt = CCJSqlParserUtil.parse(dummySql);
    net.sf.jsqlparser.statement.select.FromItem newFrom = ((PlainSelect) dummyStmt).getFromItem();
    select.setFromItem(newFrom);
    System.out.println("Rewritten SELECT: " + select);

    // UPDATE
    String updateQuery =
        "UPDATE document d SET name = 'test' WHERE d.status = 'open' OR d.status = 'new'";
    Update updateStmt = (Update) CCJSqlParserUtil.parse(updateQuery);
    String updateFilter =
        "current_setting('app.current_tenants', true) IS NULL OR current_setting('app.current_tenants', true) = '' OR d.tenant_id = ANY(string_to_array(current_setting('app.current_tenants', true), ','))";

    // For UPDATE, we can't use FromItem subquery. We must wrap the where in parenthesis.
    String newWhere = "(" + updateStmt.getWhere().toString() + ") AND (" + updateFilter + ")";
    updateStmt.setWhere(CCJSqlParserUtil.parseCondExpression(newWhere));
    System.out.println("Rewritten UPDATE: " + updateStmt.toString());

    // DELETE
    String deleteQuery = "DELETE FROM document d WHERE d.status = 'open' OR d.status = 'new'";
    Delete deleteStmt = (Delete) CCJSqlParserUtil.parse(deleteQuery);
    String deleteWhere = "(" + deleteStmt.getWhere().toString() + ") AND (" + updateFilter + ")";
    deleteStmt.setWhere(CCJSqlParserUtil.parseCondExpression(deleteWhere));
    System.out.println("Rewritten DELETE: " + deleteStmt);
  }
}
