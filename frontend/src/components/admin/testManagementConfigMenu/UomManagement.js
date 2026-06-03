import React, { useContext } from "react";
import {
  Heading,
  Grid,
  Column,
  Section,
  UnorderedList,
  ClickableTile,
} from "@carbon/react";
import { NotificationContext } from "../../layout/Layout.js";
import { AlertDialog } from "../../common/CustomNotification.js";
import { FormattedMessage, injectIntl } from "react-intl";
import PageBreadCrumb from "../../common/PageBreadCrumb.js";

let breadcrumbs = [
  { label: "home.label", link: "/" },
  { label: "breadcrums.admin.managment", link: "/MasterListsPage" },
  {
    label: "master.lists.page.test.management",
    link: "/MasterListsPage/testManagementConfigMenu",
  },
  {
    label: "configuration.uom.manage",
    link: "/MasterListsPage/UomManagement",
  },
];

function UomManagement() {
  const { notificationVisible } = useContext(NotificationContext);

  return (
    <>
      {notificationVisible === true ? <AlertDialog /> : ""}
      <div className="adminPageContent">
        <PageBreadCrumb breadcrumbs={breadcrumbs} />
        <div className="orderLegendBody">
          <Grid fullWidth={true}>
            <Column lg={16} md={8} sm={4}>
              <Section>
                <Heading>
                  <FormattedMessage id="configuration.uom.manage" />
                </Heading>
              </Section>
            </Column>
          </Grid>
          <br />
          <hr />
          <br />
          <Grid fullWidth={true}>
            <Column lg={16} md={8} sm={4}>
              <UnorderedList>
                <ClickableTile href="/MasterListsPage/UomCreate" id="UomCreate">
                  <FormattedMessage id="configuration.uom.create" />
                </ClickableTile>
                <br />
                <ClickableTile
                  href="/MasterListsPage/SampleTypeUomAssign"
                  id="SampleTypeUomAssign"
                >
                  <FormattedMessage id="configuration.uom.assign" />
                </ClickableTile>
              </UnorderedList>
            </Column>
          </Grid>
        </div>
      </div>
    </>
  );
}

export default injectIntl(UomManagement);
