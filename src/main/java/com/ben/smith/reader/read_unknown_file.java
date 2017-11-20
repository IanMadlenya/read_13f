package com.ben.smith.reader;

import java.io.*;
import java.util.*;

/**
 * Created by bensmith on 11/4/17.
 * Main file for reading stuff
 */
public class read_unknown_file {

    /*
        Simplified version of a program I previously wrote to read and process the contents
        of 13f's into a database.
    */

    public static void main(String[] args) {

//        String db_name = "filings.db";
//
//        List<String> ciks = new ArrayList<>();
//        ciks.add("0001607863");
//        ciks.add("0000919859");
//
//        pass_date(ciks, db_name, "db");

        menu();
    }

    // This is to be used later when users can select what they are looking to do
    public static void menu() {

        Scanner sc = new Scanner(System.in);

        /*
            Enable selecting how many files you want to retrieve, make default
            be the constant value we set
        */

        String intro = "\nEnter the (c)haracter from the options listed (ie c in this case)";
        String options =
            "Read data into a sqlite3 (d)atabase\n" +
            "Read data into a (c)sv file\n" +
            "(q)uit\n>";
        String extension_reminder = "Remember to enter the file extension of " +
                "your .db or .csv file.";

        String db_name;
        String csv_name;
        List<String> cik_list;

        System.out.println(intro);
        System.out.print(options);
        String user_input = sc.nextLine();
        System.out.println(extension_reminder);
        switch (user_input.toLowerCase()) {
            case "#":
                return;
            case "d":
                System.out.print("Enter the name of the database you would like to use" +
                        "\n>");
                db_name = sc.nextLine();
                cik_list = get_ciks();
                pass_date(cik_list, db_name, "db");
                System.out.println("Your data should be in a table called assets in your db");
                break;
            case "c":
                System.out.print("Enter the name of the csv you would like to use" +
                        "\n>");
                csv_name = sc.nextLine();
                cik_list = get_ciks();
                pass_date(cik_list, csv_name, "csv");
                break;
            default:
                System.out.println("I did not catch that, enter # to quit " +
                        "or press ctrl-c on your keyboard.");
        }


    }

    public static List<String> get_ciks() {
        List<String> cik_list = new ArrayList<>();

        String cik_instructions = "Now you can enter CIK numbers one at a time\n" +
                "When you are finished entering ciks press # then press enter.";
        String get_cik = "Enter a CIK or # then press enter\n>";

        Scanner sc = new Scanner(System.in);

        System.out.println(cik_instructions);
        String local_cik;
        for(;true;) {
            System.out.print(get_cik);
            local_cik = sc.nextLine();
            if(local_cik.equals("#")) {
                sc.close();
                return cik_list;
            }
            cik_list.add(local_cik);
        }
    }


    // The abstraction of passing data, pass your params and this does the rest
    public static void pass_date(List<String> ciks, String output_thing_name, String pass_to) {

        for(String cik : ciks) {
            System.out.println("Obtaining Documents for: " + cik);
            List<String> urls = web_scraper.createFinDocs(cik);
            if(pass_to.equals("csv")) {
                add_to_csv(urls, output_thing_name);
            } else if(pass_to.equals("db")) {
                add_to_database(urls, output_thing_name);
            } else {
                System.out.println("Chose csv or db. Exiting");
                return;
            }
        }

    }

    public static void add_to_csv(List<String> urls, String csv_name) {

        for(String url : urls) {
            List<String> text_lines = web_scraper.get_file_contents(url);
            List<Asset> assets = pass_to_processors(text_lines);

            output.print_to_csv(global_constants.output_dir + csv_name, assets);
        }
    }

    // Add all of the data from a file to our database
    public static void add_to_database(List<String> urls, String db_name) {

        Map<String, Set<String>> cik_to_conf_period
                = database_layer.get_added_files(db_name);

        for(String url : urls) {
            List<String> text_lines = web_scraper.get_file_contents(url);
            List<Asset> assets = pass_to_processors(text_lines);
            if (assets.size() == 0) return;

            String cik = assets.get(0).getCik();
            String conf_period = assets.get(0).getConfirmation_period();

            if (cik_to_conf_period.containsKey(cik)) {
                Set<String> conf_periods = cik_to_conf_period.get(cik);
                if (conf_periods.contains(conf_period)) {
                    System.out.printf("Unable to add %s at %s!\n", cik, conf_period);
                    continue;
                }
            }

            database_layer.add_date(db_name, assets);
        }
    }

    // Determine which file processor we need to pass our file to and get the assets from that file
    public static List<Asset> pass_to_processors(List<String> text_lines) {
        List<Asset> assets = new ArrayList<>();

        String file_type = determine_file_type(text_lines);
        if (file_type.equals("#")) {
            System.out.println("File type unknown!");
            return assets;
        } else if (file_type.equals("old_1")) {
            assets = file_processor_old.get_assets(text_lines);
            assets = add_asset_headers(assets, text_lines);
        } else {
            assets = file_processor_new.get_assets(text_lines, file_type);
            assets = add_asset_headers(assets, text_lines);
        }
        return assets;
    }

    // Add the cik and confirmation period to all of the assets
    private static List<Asset> add_asset_headers(List<Asset> assets, List<String> text_lines) {

        String[] header = document_header_getter(text_lines);

        List<Asset> assets_fixed = new ArrayList<>();

        for(Asset a : assets) {
            a.add_identifying_info(header[0], header[1]);
            assets_fixed.add(a);
        }

        return assets_fixed;
    }

    // Used for determining what we will need to read the document with
    static String determine_file_type(List<String> text_lines) {

        // Check for old_1
        for(String line : text_lines) {
            if (line.trim().equals("FORM 13F INFORMATION TABLE")) {
                return "old_1";
            }
        }
        String xml_type = xml_type_getter(text_lines);

        return xml_type;
    }

    // Read a file into a string list
    static List<String> read_file(String filename) {
        List<String> text_lines = new ArrayList<String>();

        try {
            BufferedReader br = new BufferedReader(new FileReader(filename));
            String line = br.readLine();

            while (line != null) {
                text_lines.add(line);
                line = br.readLine();
            }
            br.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return text_lines;
    }

    // Get the type xml variant used in the 13f
    private static String xml_type_getter(List<String> text_lines) {
        int text_lines_length = text_lines.size();
        text_lines = text_lines.subList(text_lines_length - 10, text_lines_length);
        for(int i = 0; i < text_lines.size(); i++) {
            text_lines.set(i, text_lines.get(i).replaceAll("\\s+",""));
        }

        String xml_type = "#";

        for(String line : text_lines) {
            String[] elements = line.split(":");
            if (elements[0].equals("</informationTable>")) {
                xml_type = "";
                break;
            } else if (elements[0].equals("</n1")) {
                xml_type = "n1";
                break;
            } else if (elements[0].equals("</ns1")) {
                xml_type = "ns1";
                break;
            }
        }

        return xml_type;
    }


    /*
     Gets the cik and the CONFORMED PERIOD OF REPORT from a 13f

     We are also addressing ammended (13f-hr/a) files here,
     we just want to skip them as for now, we may want to keep later
     in the future there may be an option involving changing global variables
     */
    private static String[] document_header_getter(List<String> text_lines) {
        for(int i = 0; i < text_lines.size(); i++) {
            text_lines.set(i, text_lines.get(i).replaceAll("\\s+",""));
        }

        String cik = "#";
        String report_period = "#";

        for(String line : text_lines) {
            String[] elements = line.split(":");
            if(elements[0].equals("CENTRALINDEXKEY")) {
                cik = elements[1];
            } else if(elements[0].equals("CONFORMEDPERIODOFREPORT")) {
                report_period = elements[1];
            }
            if(line.equals("CONFORMEDSUBMISSIONTYPE:13F-HR/A")) {
                return new String[]{"#", "#"};
            }
        }

        return new String[]{cik, report_period};
    }


    // This is being used for testing later on
    // Print the contents of a directory
    public static void print_files_in_directory(String directory) {
        File folder = new File(directory);
        File[] listOfFiles = folder.listFiles();
        for (int i = 0; i < listOfFiles.length; i++) {
            if (listOfFiles[i].isFile()) {
                System.out.println("File " + listOfFiles[i].getName());
            } else if (listOfFiles[i].isDirectory()) {
                System.out.println("Directory " + listOfFiles[i].getName());
            }
        }
    }


}
