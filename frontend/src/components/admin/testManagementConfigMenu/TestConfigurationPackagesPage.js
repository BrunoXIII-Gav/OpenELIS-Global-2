import React from "react";
import { Column, Grid, Section } from "@carbon/react";
import PageBreadCrumb from "../../common/PageBreadCrumb.js";
import TestConfigurationPackages from "./TestConfigurationPackages.js";

const breadcrumbs = [
  { label: "home.label", link: "/" },
  { label: "breadcrums.admin.managment", link: "/MasterListsPage" },
  {
    label: "testPackage.title",
    link: "/MasterListsPage/TestConfigurationPackages",
  },
];

export default function TestConfigurationPackagesPage() {
  return (
    <div className="adminPageContent">
      <PageBreadCrumb breadcrumbs={breadcrumbs} />
      <div className="orderLegendBody">
        <Grid fullWidth={true}>
          <Column lg={16} md={8} sm={4}>
            <Section>
              <TestConfigurationPackages />
            </Section>
          </Column>
        </Grid>
      </div>
    </div>
  );
}
