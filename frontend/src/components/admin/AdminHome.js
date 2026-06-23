import React from "react";
import { ClickableTile, Column, Grid, Heading, Section } from "@carbon/react";
import {
  CicsSystemGroup,
  Edit,
  ListDropdown,
  ResultNew,
  Settings,
  User,
} from "@carbon/icons-react";
import { FormattedMessage } from "react-intl";
import { useHistory, useRouteMatch } from "react-router-dom";
import PageBreadCrumb from "../common/PageBreadCrumb";

const breadcrumbs = [
  { label: "home.label", link: "/" },
  { label: "breadcrums.admin.managment", link: "/MasterListsPage" },
];

function AdminHome() {
  const history = useHistory();
  const { path } = useRouteMatch();

  const quickLinks = [
    {
      id: "adminQuickUsers",
      labelId: "unifiedSystemUser.browser.title",
      href: `${path}/userManagement`,
      icon: User,
    },
    {
      id: "adminQuickProviders",
      labelId: "provider.browse.title",
      href: `${path}/providerMenu`,
      icon: CicsSystemGroup,
    },
    {
      id: "adminQuickTests",
      labelId: "master.lists.page.test.management",
      href: `${path}/testManagementConfigMenu`,
      icon: ResultNew,
    },
    {
      id: "adminQuickModifyTests",
      labelId: "configuration.test.modify",
      href: `${path}/TestModifyEntry`,
      icon: Edit,
    },
    {
      id: "adminQuickOrderFields",
      labelId: "order.additional.fields.menu",
      href: `${path}/OrderAdditionalFields`,
      icon: Settings,
    },
    {
      id: "adminQuickPatientFields",
      labelId: "patient.additional.fields.menu",
      href: `${path}/PatientAdditionalFields`,
      icon: Settings,
    },
    {
      id: "adminQuickSampleFields",
      labelId: "configuration.sampleType.additional.fields",
      href: `${path}/SampleTypeAdditionalFields`,
      icon: ListDropdown,
    },
  ];

  const handleTileClick = (href) => (event) => {
    event.preventDefault();
    history.push(href);
  };

  return (
    <div className="adminPageContent">
      <PageBreadCrumb breadcrumbs={breadcrumbs} />
      <Grid fullWidth={true}>
        <Column lg={16} md={8} sm={4}>
          <Section>
            <Heading>
              <FormattedMessage id="breadcrums.admin.managment" />
            </Heading>
          </Section>
        </Column>
      </Grid>
      <Grid fullWidth={true} className="admin-quick-links">
        {quickLinks.map((link) => {
          const Icon = link.icon;
          return (
            <Column
              key={link.id}
              lg={4}
              md={4}
              sm={4}
              className="admin-quick-link-column"
            >
              <ClickableTile
                id={link.id}
                href={link.href}
                onClick={handleTileClick(link.href)}
                className="admin-quick-link-tile"
              >
                <div className="admin-quick-link-content">
                  <span className="admin-quick-link-icon">
                    <Icon size={24} />
                  </span>
                  <span className="admin-quick-link-title">
                    <FormattedMessage id={link.labelId} />
                  </span>
                </div>
              </ClickableTile>
            </Column>
          );
        })}
      </Grid>
    </div>
  );
}

export default AdminHome;
