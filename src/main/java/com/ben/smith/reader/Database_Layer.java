package com.ben.smith.reader;

import java.io.File;
import java.sql.*;
import java.util.*;

/**
 * Created by bensmith on 11/11/17.
 * This file handles any db stuff we will deal with in the program
 */
public class Database_Layer {

    public static void main(String[] args) {

        String db_name = "filings.db";

        create_database(db_name);
//        connect(db_name);
    }

    // Adds data to a databse
    public static void add_date(String db_name, List<Asset> assets) {
        // Ensures that there is a db to add data to.
        create_database(db_name);

        Connection conn = null;
        Asset b = assets.get(0);
        try {
            String url = Global_Constants.jdbc_type + Global_Constants.db_location + db_name;

            // create a connection to the database
            conn = DriverManager.getConnection(url);

            conn.setAutoCommit(false);

            PreparedStatement ps =
                    conn.prepareStatement("insert into Assets values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");

            // Add each asset to a batch statement to add all the data at once.
            for(Asset a : assets) {
                b = a;
                int cash_val = Integer.parseInt(a.getCash_value().replaceAll(",", ""));
                int num_shares = Integer.parseInt(a.getNum_shares().replaceAll(",", ""));
                java.sql.Date conf_period = java.sql.Date.valueOf(a.getConfirmation_period());
                java.sql.Date sub_date = java.sql.Date.valueOf(a.getSubmit_date());

                ps.setString(1, a.getCik());
                ps.setDate(2, conf_period);
                ps.setString(3, a.getName());
                ps.setString(4, a.getTitle());
                ps.setString(5, a.getCusip());
                ps.setString(6, a.getExcel_cusip());
                ps.setInt(7, cash_val);
                ps.setInt(8, num_shares);
                ps.setString(9, a.getType());
                ps.setString(10, a.getDiscretion());
                ps.setDate(11, sub_date);

                ps.executeUpdate(); //JDBC queues this for later execution
            }

            ps.executeBatch();
            conn.commit();

            ps.close();


        } catch (SQLException e) {
            System.out.println(e.getMessage());
            b.print_all_fields();
        } finally {
            try {
                if (conn != null) {
                    conn.close();
                }
            } catch (SQLException ex) {
                System.out.println(ex.getMessage());
            }
        }

    }

    /*
        Collect the info to know if we have previously read this 13f
        We can determine if we have read a 13f previously by looking at
        its cik and confirmation period. If our database already contains
        that unique combination then we have already processed it into
        our database, so we do not need to add it again.
    */
    public static Map<String, Set<String>> get_added_files(String db_name) {

        Map<String, Set<String>> cik_to_conf_period = new HashMap<>();

        // Check to make sure we only check the table if it exists
        File f = new File(Global_Constants.db_location + db_name);
        if(!(f.exists() && !f.isDirectory())) {
            return cik_to_conf_period;
        }

        Connection conn = null;
        try {
            String url = Global_Constants.jdbc_type + Global_Constants.db_location + db_name;

            // create a connection to the database
            conn = DriverManager.getConnection(url);

            Statement stmt = conn.createStatement(
                    ResultSet.TYPE_FORWARD_ONLY,
                    ResultSet.CONCUR_READ_ONLY);

            // A particular filing is identifiable using a cik and confirmation period
            ResultSet rs = stmt.executeQuery("select distinct cik, confirmation_period from assets;");

            while (rs.next()) {
                String cik = rs.getString("cik");
                String conf_period = rs.getString("confirmation_period");

                // Convert the time stored on the db (milliseconds) and convert to yyyy-mm-dd
                Calendar calendar = Calendar.getInstance();
                calendar.setTimeInMillis(Long.parseLong(conf_period));

                int mYear = calendar.get(Calendar.YEAR);
                int mMonth = calendar.get(Calendar.MONTH) + 1; // Month starts at 0 -> Jan
                int mDay = calendar.get(Calendar.DAY_OF_MONTH);

                // Change month so that it is always mm instead of m
                String month = Integer.toString(mMonth);
                if(mMonth < 10) {
                    month = "0" + month;
                }

                // Change day so that it is always dd instead of d
                String day = Integer.toString(mDay);
                if(mDay < 10) {
                    day = "0" + day;
                }

                // Combine the date into yyyy-mm-dd
                conf_period = mYear + "-" + month + "-" + day;

                // Add all of the confirmation periods we have seen to their associated keys
                if(cik_to_conf_period.containsKey(cik)) {
                    Set<String> conf_periods = cik_to_conf_period.get(cik);
                    conf_periods.add(conf_period);
                    cik_to_conf_period.put(cik, conf_periods);
                } else {
                    Set<String> conf_periods = new HashSet<>();
                    conf_periods.add(conf_period);
                    cik_to_conf_period.put(cik, conf_periods);
                }
            }

        } catch (SQLException e) {
            System.out.println(e.getMessage());
        } finally {
            try {
                if (conn != null) {
                    conn.close();
                }
            } catch (SQLException ex) {
                System.out.println(ex.getMessage());
            }
        }

        // Return our confirmation periods mapped to their respective ciks
        return cik_to_conf_period;
    }

    /*
        Create the database and table if it does not already exist
        We have no primary key because sometimes a firm will have 2
        separate holdings of the same security so there is a possible
        situation where every value in one row is identical to that
        of another row.
    */
    public static void create_database(String db_name) {
        Connection conn = null;

        String sqlCreate =
                String.format(
                        "CREATE TABLE IF NOT EXISTS Assets(\n " +
                        "cik text NOT NULL,\n" +
                        "confirmation_period DATE NOT NULL,\n" +
                        "name text,\n" +
                        "title text,\n" +
                        "cusip text NOT NULL,\n" +
                        "excel_cusip text,\n" +
                        "cash_value integer,\n" +
                        "num_shares integer,\n" +
                        "type text,\n" +
                        "discretion text,\n" +
                        "submit_date date);"
                );

        try {
            String url = Global_Constants.jdbc_type + Global_Constants.db_location + db_name;
            // create a connection to the database

            conn = DriverManager.getConnection(url);
            Statement stmt = conn.createStatement();
            stmt.execute(sqlCreate);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
